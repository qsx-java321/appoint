package com.qsx.appoint.support;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 独立rabbit-it档位：真实MySQL、Redis与RabbitMQ容器，不使用常驻资源。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("rabbit-it")
@TestPropertySource(properties = {
        "spring.config.location=classpath:/rabbit-it/",
        "spring.config.import=",
        "spring.config.additional-location=",
        "spring.config.name=application",
        "appoint.local-environment.enabled=false"
})
@ContextConfiguration(initializers = RegressionEnvironment.class)
@Import({MySqlContainerConfiguration.class,
        RabbitMQContainerConfiguration.class,
        AppointmentClockTestConfiguration.class,
        FormalPaymentTestSupport.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
public @interface RabbitItTest {
    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};
}
