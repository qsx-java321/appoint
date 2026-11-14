package com.qsx.appoint.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

/** 加载test上下文验证旧simulate-success路由未注册；与dev/prod各自的装配保障独立保留。 */
@RegressionTest(properties={"appoint.messaging.enabled=false", "appoint.expiration.enabled=false"})
@ActiveProfiles("test")
class LegacyTestRouteAbsenceTest {
    @Autowired private RequestMappingHandlerMapping mappings;
    @Test
    void oldPaymentSuccessRouteIsNotRegistered() {
        assertThat(mappings.getHandlerMethods().keySet()).noneMatch(mapping -> mapping.toString().contains("simulate-success"));
    }
}
