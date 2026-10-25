package com.qsx.appoint.payment.service;

import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.payment.dto.PaymentResponse;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
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
    @Mock private PaymentOrderMapper paymentOrderMapper;
    @Mock private AppointmentOrderMapper appointmentOrderMapper;
    @Mock private ScheduleMapper scheduleMapper;
    @InjectMocks private PaymentServiceImpl paymentService;

    @Test
    void paidOrderReturnsCurrentStateWithoutAdditionalUpdates() {
        PaymentOrder payment = new PaymentOrder();
        payment.setId(1L);
        payment.setPaymentNo("PAY1");
        payment.setUserId(10L);
        payment.setAppointmentOrderId(2L);
        payment.setStatus("PAID");
        AppointmentOrder appointment = new AppointmentOrder();
        appointment.setId(2L);
        appointment.setStatus("CONFIRMED");

        when(paymentOrderMapper.selectById(1L)).thenReturn(payment);
        when(appointmentOrderMapper.selectById(2L)).thenReturn(appointment);

        PaymentResponse response = paymentService.simulateSuccess(10L, 1L);

        assertThat(response.getPaymentStatus()).isEqualTo("PAID");
        assertThat(response.getAppointmentStatus()).isEqualTo("CONFIRMED");
        verify(paymentOrderMapper, never()).payIfUnpaid(anyLong());
        verify(appointmentOrderMapper, never()).confirmIfPending(anyLong());
        verify(scheduleMapper, never()).bookIfHeld(anyLong());
    }
}
