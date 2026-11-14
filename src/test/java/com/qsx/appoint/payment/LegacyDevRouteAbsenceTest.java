package com.qsx.appoint.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

/** 加载dev测试上下文，验证旧simulate-success路由未注册；不是访问日常运行中的应用。 */
@RegressionTest(properties={"appoint.messaging.enabled=false", "appoint.expiration.enabled=false"})
@ActiveProfiles("dev")
class LegacyDevRouteAbsenceTest {
    @Autowired private RequestMappingHandlerMapping mappings;
    @Test
    void oldPaymentSuccessRouteIsNotRegistered() {
        assertThat(mappings.getHandlerMethods().keySet()).noneMatch(mapping -> mapping.toString().contains("simulate-success"));
    }
}
