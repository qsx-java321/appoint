package com.qsx.appoint.payment;

import com.qsx.appoint.payment.adapter.mock.MockPayController;
import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.port.PaymentGatewayRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gateway-production;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.expiration.enabled=false",
        "appoint.messaging.enabled=false"
})
@ActiveProfiles("prod")
class MockPayProductionProfileTest {
    @Autowired private ApplicationContext context;
    @Autowired private PaymentGatewayRegistry registry;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    @Test
    void productionDoesNotRegisterMockAdapterOrController() {
        assertThat(context.getBeansOfType(MockPaymentGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(MockPayController.class)).isEmpty();
        assertThatThrownBy(() -> registry.require(PaymentChannel.MOCK)).isInstanceOf(RuntimeException.class);
        assertThat(handlerMapping.getHandlerMethods().keySet())
                .noneMatch(mapping -> mapping.toString().contains("simulate-success")
                        || mapping.toString().contains("/api/v1/mock-pay/"));
    }
}
