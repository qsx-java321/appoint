package com.qsx.appoint.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** 也可从 IDE 单独运行；外部 SPRING_DATASOURCE_* 不能改变该上下文。 */
@RegressionTest
class RegressionIsolationTest {
    @Autowired DataSource dataSource;
    @Autowired Environment environment;

    @Test void usesOnlyRegressionConfiguration() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
        assertThat(environment.getProperty("appoint.local-environment.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.config.location")).isEqualTo("classpath:/regression/");
        assertThat(environment.getProperty("spring.config.import")).isEmpty();
    }
}
