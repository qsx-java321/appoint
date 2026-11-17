package com.qsx.appoint.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import java.lang.annotation.*;

/** MockMvc回归使用独立配置根，Maven与IDE均不导入常驻连接。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@org.springframework.context.annotation.Import(RegistrationTestSupport.class)
@SpringBootTest
@TestPropertySource(properties = {
        "spring.config.location=classpath:/regression/",
        "spring.config.import=",
        "spring.config.additional-location=",
        "spring.config.name=application",
        "appoint.local-environment.enabled=false"
})
@ContextConfiguration(initializers = RegressionEnvironment.class)
public @interface RegressionTest {
    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};
    @AliasFor(annotation = SpringBootTest.class, attribute = "classes")
    Class<?>[] classes() default {};
    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;
}
