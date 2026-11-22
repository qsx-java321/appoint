package com.qsx.appoint.support;

import java.lang.annotation.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("regression")
@TestPropertySource(properties = {
        "spring.config.location=classpath:/mysql-it/",
        "spring.config.import=",
        "spring.config.additional-location=",
        "spring.config.name=application",
        "appoint.local-environment.enabled=false"
})
@ContextConfiguration(initializers = RegressionEnvironment.class)
@Import({MySqlContainerConfiguration.class,
        AppointmentClockTestConfiguration.class,
        FormalPaymentTestSupport.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
public @interface MySqlIntegrationTest {
    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};
}
