package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

/** 直接调用组装器，验证支付与取消权限投影；不启动Spring。 */
class AppointmentResponseAssemblerTest {
    private final AppointmentResponseAssembler assembler = new AppointmentResponseAssembler(new BookingWorkflowProjector());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 8, 0);

    @Test
    void legacySignedVisitKeepsFirstTimeAndSnapshotWindowAfterClosing() {
        var record = new BookingQueryRecord();
        record.setAppointmentUserId(1L); record.setPaymentUserId(1L); record.setPaymentOrderId(2L);
        record.setStatus("CONFIRMED"); record.setPaymentStatus("PAID");
        record.setAppointmentDate(NOW.toLocalDate());
        record.setStartTime(java.time.LocalTime.of(9, 15)); record.setEndTime(java.time.LocalTime.of(12, 30));
        record.setPatientDocumentNo("110105199001010010");
        record.setVisitRecordId(3L); record.setVisitStatus("WAITING");
        record.setCheckedInAt(NOW.toLocalDate().atStartOfDay());
        var visit = assembler.assemble(record, NOW.plusDays(1)).getVisit();
        assertThat(visit.checkedInAt()).isEqualTo(NOW.toLocalDate().atStartOfDay());
        assertThat(visit.status()).isEqualTo("WAITING");
        assertThat(visit.canCheckIn()).isFalse();
        assertThat(visit.checkInOpensAt()).isEqualTo(NOW.toLocalDate().atTime(8, 45));
        assertThat(visit.checkInClosesAt()).isEqualTo(NOW.toLocalDate().atTime(12, 30));
    }

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
        record.setEndTime(NOW.plusHours(2).toLocalTime());
        record.setPatientDocumentNo("110105199001010010");
        var response = assembler.assemble(record, NOW);
        assertThat(response.isCanPay()).isEqualTo(canPay);
        assertThat(response.isCanCancel()).isEqualTo(canCancel);
    }
}
