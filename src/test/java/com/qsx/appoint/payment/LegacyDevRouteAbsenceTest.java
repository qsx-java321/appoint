package com.qsx.appoint.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"appoint.messaging.enabled=false", "appoint.expiration.enabled=false"})
@ActiveProfiles("dev")
class LegacyDevRouteAbsenceTest {
    @Autowired private RequestMappingHandlerMapping mappings;
    @Test
    void oldPaymentSuccessRouteIsNotRegistered() {
        assertThat(mappings.getHandlerMethods().keySet()).noneMatch(mapping -> mapping.toString().contains("simulate-success"));
    }
}
