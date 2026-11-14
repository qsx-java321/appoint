package com.qsx.appoint.localenv;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.Banner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.context.TestConfiguration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import static org.assertj.core.api.Assertions.*;

class LocalEnvironmentPolicyTest {
    @TempDir Path directory;
    MockEnvironment valid(String name) {
        var env=new MockEnvironment();env.setActiveProfiles(name);
        return env.withProperty("spring.datasource.url","jdbc:mysql://127.0.0.1:3306/"+(name.equals("dev")?"appoint":"appoint_"+name))
                .withProperty("spring.datasource.username","appoint_"+name).withProperty("spring.datasource.password","fixture")
                .withProperty("server.port",name.equals("dev")?"8080":name.equals("test")?"8081":"8082")
                .withProperty("server.address","127.0.0.1").withProperty("appoint.catalog-cache.namespace",name)
                .withProperty("spring.rabbitmq.virtual-host","/appoint-"+name).withProperty("spring.rabbitmq.host","127.0.0.1")
                .withProperty("spring.rabbitmq.username","appoint_"+name).withProperty("spring.rabbitmq.password","fixture")
                .withProperty("spring.data.redis.host","127.0.0.1").withProperty("appoint.jwt.secret","fixture-"+name+"-a-distinct-long-secret-of-at-least-48-characters")
                .withProperty("appoint.mock-pay.signing-key","fixture-key").withProperty("appoint.local-environment.seed-password","fixture-password-123");
    }
    @Test void rejectsCrossEnvironmentDatabaseNamespaceVhostPortAndMixedProfilesBeforeAnyConnection() {
        for(String name:java.util.List.of("dev","test","demo")) {
            LocalEnvironmentPolicy.validate(valid(name),name,directory);
            for(var override:java.util.Map.of("spring.datasource.url","jdbc:mysql://127.0.0.1:3306/other",
                    "appoint.catalog-cache.namespace","other","spring.rabbitmq.virtual-host","/other","server.port","8099").entrySet()) {
                var env=valid(name).withProperty(override.getKey(),override.getValue());
                assertThatThrownBy(()->LocalEnvironmentPolicy.validate(env,name,directory)).isInstanceOf(IllegalArgumentException.class);
            }
            for(String key:java.util.List.of("spring.flyway.schemas","spring.flyway.url","spring.datasource.hikari.jdbc-url","spring.rabbitmq.addresses","spring.data.redis.url")) {
                var env=valid(name).withProperty(key,"forbidden");
                assertThatThrownBy(()->LocalEnvironmentPolicy.validate(env,name,directory)).isInstanceOf(IllegalArgumentException.class);
            }
            var mixed=valid(name);mixed.setActiveProfiles("dev","demo");
            assertThatThrownBy(()->LocalEnvironmentPolicy.validate(mixed,name,directory)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsIndexedAndRelaxedConnectionOverrides() {
        for (var entry : Map.of(
                "spring.flyway.schemas[0]", "appoint",
                "spring.datasource.hikari.jdbcUrl", "jdbc:mysql://127.0.0.1:3306/appoint",
                "spring.datasource.hikari.data-source-properties.databaseName", "appoint",
                "spring.datasource.hikari.data-source-class-name", "com.mysql.cj.jdbc.MysqlDataSource",
                "spring.data.redis.cluster.nodes[0]", "127.0.0.1:6379",
                "spring.data.redis.sentinel.nodes[0]", "127.0.0.1:26379",
                "spring.autoconfigure.exclude[0]", "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        ).entrySet()) {
            var env = valid("demo").withProperty(entry.getKey(), entry.getValue());
            ConfigurationPropertySources.attach(env);
            assertThatExceptionOfType(IllegalArgumentException.class).as(entry.getKey())
                    .isThrownBy(() -> LocalEnvironmentPolicy.validate(env, "demo", directory));
        }
    }

    @Test void rejectsShellConnectionOverrides() {
        for (var entry : Map.of(
                "SPRING_DATASOURCE_HIKARI_JDBCURL", "jdbc:mysql://127.0.0.1:3306/appoint",
                "SPRING_FLYWAY_SCHEMAS_0", "appoint",
                "SPRING_DATA_REDIS_CLUSTER_NODES_0", "127.0.0.1:6379",
                "SPRING_RABBITMQ_ADDRESSES", "amqp://127.0.0.1:5672"
        ).entrySet()) {
            var env = valid("demo");
            env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("systemEnvironment",
                    Map.of(entry.getKey(), entry.getValue())));
            ConfigurationPropertySources.attach(env);
            assertThatExceptionOfType(IllegalArgumentException.class).as(entry.getKey())
                    .isThrownBy(() -> LocalEnvironmentPolicy.validate(env, "demo", directory));
        }
    }

    @Test void rejectsSharedJwtAndMissingCredentialsWithoutTouchingRealFiles() throws Exception {
        var env = valid("test");
        Files.writeString(directory.resolve(".env.dev"), "JWT_SECRET=" + env.getRequiredProperty("appoint.jwt.secret"));
        assertThatThrownBy(() -> LocalEnvironmentPolicy.validate(env, "test", directory))
                .isInstanceOf(IllegalStateException.class).hasMessage("环境JWT密钥必须独立");
        Files.delete(directory.resolve(".env.dev"));
        for (String field : java.util.List.of("spring.datasource.password", "spring.rabbitmq.password", "appoint.jwt.secret")) {
            var invalid = valid("test").withProperty(field, "");
            assertThatThrownBy(() -> LocalEnvironmentPolicy.validate(invalid, "test", directory))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsDisablingProtectionAndMigration() {
        var disabled = valid("demo").withProperty("appoint.local-environment.enabled", "false");
        assertThatThrownBy(() -> new LocalEnvironmentPolicy().postProcessEnvironment(disabled, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("常驻环境保护不能关闭");
        var noMigration = valid("demo").withProperty("spring.flyway.enabled", "false");
        assertThatThrownBy(() -> LocalEnvironmentPolicy.validate(noMigration, "demo", directory))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("常驻环境不能关闭初始化生命周期");
    }

    @Test void registeredPolicyRejectsWrongDemoDatabaseBeforeContextInitialization() {
        var env = valid("demo")
                .withProperty("appoint.local-environment.enabled", "true")
                .withProperty("appoint.local-environment.name", "demo")
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/appoint")
                .withProperty("spring.config.location", "optional:" + directory.resolve("absent.properties").toUri());
        var initialized = new AtomicBoolean();
        var application = new SpringApplication(NoBusinessBeans.class);
        application.setEnvironment(env);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.addInitializers(context -> initialized.set(true));
        assertThatThrownBy(application::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("环境目标不匹配");
        assertThat(initialized).isFalse();
    }

    @Test void malformedUrlDoesNotExposeConnectionDetails() {
        var env = valid("demo").withProperty("spring.datasource.url", "jdbc:mysql://user:private-value@bad host/appoint_demo");
        assertThatThrownBy(() -> LocalEnvironmentPolicy.validate(env, "demo", directory))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("数据库连接格式不支持")
                .hasNoCause();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NoBusinessBeans {}

    @Test void profileFilesResolveToTheirOwnTargetsAndCredentials() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var shared = loader.load("shared", new ClassPathResource("application.yml"));
        assertThat(shared.getFirst().containsProperty("spring.config.import")).isFalse();
        assertThat(shared.getFirst().getProperty("spring.profiles.default")).isEqualTo("dev");
        for (String name : java.util.List.of("dev", "test", "demo")) {
            var env = new MockEnvironment();
            env.setActiveProfiles(name);
            env.withProperty("MYSQL_USER", "appoint_" + name).withProperty("MYSQL_PASSWORD", "fixture")
                    .withProperty("RABBITMQ_USER", "appoint_" + name).withProperty("RABBITMQ_PASSWORD", "fixture")
                    .withProperty("JWT_SECRET", "fixture-" + name + "-a-distinct-secret-of-at-least-48-characters")
                    .withProperty("MOCK_PAY_SIGNING_KEY", "fixture-key")
                    .withProperty("LOCAL_SEED_PASSWORD", "fixture-password-123");
            loader.load(name, new ClassPathResource("application-" + name + ".yml"))
                    .forEach(env.getPropertySources()::addLast);
            shared.forEach(env.getPropertySources()::addLast);
            assertThat(env.getProperty("spring.config.import")).isEqualTo("file:./.env." + name + "[.properties]");
            LocalEnvironmentPolicy.validate(env, name, directory);
        }
    }
}
