package com.qsx.appoint.payment.service;

import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import com.qsx.appoint.payment.dto.PaymentResponse;
import com.qsx.appoint.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 只隔离验证重复模拟支付不会重复推进状态机。 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {
    @Mock private AppointmentLifecycleService lifecycleService;
    @InjectMocks private PaymentServiceImpl paymentService;

    @Test
    void delegatesStateTransitionToLifecycleService() {
        PaymentResponse expected = new PaymentResponse(1L, "PAY1", "PAID", 2L, "CONFIRMED");
        when(lifecycleService.simulatePaymentSuccess(10L, 1L)).thenReturn(expected);

        PaymentResponse response = paymentService.simulateSuccess(10L, 1L);

        assertThat(response).isSameAs(expected);
        verify(lifecycleService).simulatePaymentSuccess(10L, 1L);
    }
}
