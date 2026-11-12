package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

/** 直接调用组装器，验证支付与取消权限投影；不启动Spring。 */
class AppointmentResponseAssemblerTest {
    private final AppointmentResponseAssembler assembler = new AppointmentResponseAssembler(new BookingWorkflowProjector());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 8, 0);

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT,UNPAID,-1,false,true", "PENDING_PAYMENT,UNPAID,0,false,true",
            "PENDING_PAYMENT,UNPAID,1,true,true", "PENDING_PAYMENT,PAYING,-1,false,true",
            "PENDING_PAYMENT,PAID,1,false,false", "CONFIRMED,PAID,1,false,false"
    })
    void preservesPaymentAndCancellationPermissions(String appointment, String payment, int deadlineOffset,
                                                     boolean canPay, boolean canCancel) {
        BookingQueryRecord record = new BookingQueryRecord();
        record.setId(1L);
        record.setAppointmentUserId(1L);
        record.setPaymentUserId(1L);
        record.setPaymentOrderId(2L);
        record.setStatus(appointment);
        record.setPaymentStatus(payment);
        record.setPaymentExpiresAt(NOW.plusSeconds(deadlineOffset));
        record.setAppointmentDate(NOW.toLocalDate().plusDays(1));
        record.setStartTime(NOW.toLocalTime());
        record.setPatientDocumentNo("110105199001010010");
        var response = assembler.assemble(record, NOW);
        assertThat(response.isCanPay()).isEqualTo(canPay);
        assertThat(response.isCanCancel()).isEqualTo(canCancel);
    }
}
