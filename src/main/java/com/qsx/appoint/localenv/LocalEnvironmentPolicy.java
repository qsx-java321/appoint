package com.qsx.appoint.localenv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import java.util.Arrays;
import java.util.Set;
import java.util.List;
import java.util.Properties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;

/** 不能用Shell关闭常驻保护；独立回归通过明确配置根完全绕开主profile文件。 */
public class LocalEnvironmentPolicy implements EnvironmentPostProcessor, Ordered {
    static final int BACKEND_PORT = 8080;
    static final int FRONTEND_PORT = 5173;

    @Override
    public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String root = env.getProperty("spring.config.location", "");
        if (root.equals("classpath:/regression/")) return;
        String[] profiles = env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles();
        if (Arrays.stream(profiles).anyMatch(Set.of("dev", "test", "demo")::contains)) {
            if (!env.getProperty("appoint.local-environment.enabled", Boolean.class, false))
                throw new IllegalStateException("常驻环境保护不能关闭");
            validate(env, env.getRequiredProperty("appoint.local-environment.name"));
        }
    }

    public static void validate(ConfigurableEnvironment env, String name) {
        validate(env, name, Path.of("."));
    }

    // 测试使用临时凭据目录，避免读取本机私密配置。
    static void validate(ConfigurableEnvironment env, String name, Path directory) {
        if (!List.of("dev", "test", "demo").contains(name)) throw new IllegalArgumentException("未知环境");
        String[] profiles = env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles();
        if (profiles.length != 1 || !profiles[0].equals(name)) throw new IllegalArgumentException("只允许单个常驻profile");
        boolean isolated = "classpath:/regression/".equals(env.getProperty("spring.config.location"));
        int actualPort = env.getRequiredProperty("server.port", Integer.class);
        if (isolated && List.of(BACKEND_PORT, FRONTEND_PORT).contains(actualPort)) throw new IllegalArgumentException("隔离验证不能占用常驻端口");
        String db = name.equals("dev") ? "appoint" : "appoint_" + name;
        for (String override : List.of("spring.flyway.url", "spring.flyway.user", "spring.flyway.password",
                "spring.flyway.schemas", "spring.flyway.default-schema", "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.username", "spring.datasource.hikari.password", "spring.datasource.hikari.catalog",
                "spring.datasource.hikari.schema", "spring.datasource.hikari.connection-init-sql",
                "spring.datasource.hikari.data-source-properties", "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-j-n-d-i", "spring.datasource.jndi-name", "spring.datasource.type",
                "spring.rabbitmq.addresses", "spring.data.redis.url", "spring.data.redis.cluster",
                "spring.data.redis.sentinel", "spring.autoconfigure.exclude")) {
            if (hasProperty(env, override)) throw new IllegalArgumentException("不支持覆盖底层连接或迁移目标");
        }
        if (!env.getProperty("spring.flyway.enabled", Boolean.class, true))
            throw new IllegalArgumentException("常驻环境不能关闭初始化生命周期");
        String account = env.getRequiredProperty("spring.datasource.username");
        if (!isolated && (account.equalsIgnoreCase("root") || (!name.equals("dev") && !account.equals("appoint_" + name))))
            throw new IllegalArgumentException("常驻应用必须使用环境专用数据库账号");
        if (!isolated && !env.getRequiredProperty("spring.rabbitmq.username").equals("appoint_" + name))
            throw new IllegalArgumentException("常驻应用必须使用环境专用RabbitMQ账号");
        String url = env.getRequiredProperty("spring.datasource.url");
        URI uri;
        try {
            if (!url.startsWith("jdbc:mysql:")) throw new IllegalArgumentException();
            uri = URI.create(url.substring("jdbc:".length()));
        } catch (IllegalArgumentException error) {
            // URI异常可能带出完整URL及其中的凭据。
            throw new IllegalArgumentException("数据库连接格式不支持");
        }
        if (uri.getUserInfo() != null || !("/" + db).equals(uri.getPath())
                || !List.of("localhost", "127.0.0.1").contains(uri.getHost()) || (!isolated && uri.getPort() != 3306) || (isolated && uri.getPort() == 3306)
                || (!isolated && actualPort != BACKEND_PORT)
                || !name.equals(env.getRequiredProperty("appoint.catalog-cache.namespace"))
                || !("/appoint-" + name).equals(env.getRequiredProperty("spring.rabbitmq.virtual-host"))
                || !List.of("localhost", "127.0.0.1").contains(env.getProperty("spring.rabbitmq.host"))
                || (!isolated && env.getProperty("spring.rabbitmq.port", Integer.class, 5672) != 5672)
                || (isolated && env.getProperty("spring.rabbitmq.port", Integer.class, 5672) == 5672)
                || !List.of("localhost", "127.0.0.1").contains(env.getProperty("spring.data.redis.host"))
                || (!isolated && env.getProperty("spring.data.redis.port", Integer.class, 6379) != 6379)
                || (isolated && env.getProperty("spring.data.redis.port", Integer.class, 6379) == 6379)
                || !"127.0.0.1".equals(env.getProperty("server.address"))) throw new IllegalArgumentException("环境目标不匹配");
        for (String field : List.of("spring.datasource.username", "spring.datasource.password", "spring.rabbitmq.username",
                "spring.rabbitmq.password", "appoint.jwt.secret", "appoint.mock-pay.signing-key")) {
            String value = env.getRequiredProperty(field);
            if (value.isBlank() || value.startsWith("replace-")) throw new IllegalArgumentException("缺少本地配置");
        }
        if (env.getRequiredProperty("appoint.jwt.secret").length() < 32) throw new IllegalArgumentException("JWT密钥过短");
        if (env.getProperty("appoint.local-environment.seed-password", "").length() < 12)
            throw new IllegalArgumentException("初始化账号密码至少12位");
        if (!isolated) verifyDistinctJwt(name, env.getRequiredProperty("appoint.jwt.secret"), directory);
    }

    // 与Spring绑定使用同一套名称映射，同时识别camelCase、环境变量和索引列表。
    private static boolean hasProperty(ConfigurableEnvironment env, String key) {
        var name = ConfigurationPropertyName.of(key);
        for (var source : ConfigurationPropertySources.get(env)) {
            if (source.getConfigurationProperty(name) != null
                    || source.containsDescendantOf(name) == ConfigurationPropertyState.PRESENT) return true;
        }
        return false;
    }

    private static void verifyDistinctJwt(String name, String secret, Path directory) {
        for (String other : List.of("dev", "test", "demo")) {
            if (other.equals(name)) continue;
            Path file = directory.resolve(".env." + other);
            if (Files.exists(file)) {
                var values = new Properties();
                try (var reader = Files.newBufferedReader(file)) { values.load(reader); }
                catch (Exception e) { throw new IllegalStateException("无法检查环境密钥隔离"); }
                if (secret.equals(values.getProperty("JWT_SECRET"))) throw new IllegalStateException("环境JWT密钥必须独立");
            }
        }
    }
}
