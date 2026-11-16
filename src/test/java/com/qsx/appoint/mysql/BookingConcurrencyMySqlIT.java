package com.qsx.appoint.mysql;

import com.qsx.appoint.support.MySqlBusinessTestSupport;
import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.common.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.Callable;
import static org.assertj.core.api.Assertions.*;

/** 多个独立业务事务竞争真实唯一键和行锁；SQL异常直接使测试失败。 */
class BookingConcurrencyMySqlIT extends MySqlBusinessTestSupport {
    private List<Actor> actors() { return List.of(actor("110105199001010010"),actor("110105199002020026"),actor("110105199003030031"),actor("110105199004040048")); }
    private List<Object> compete(List<Actor> actors) throws Exception {
        var tasks=new ArrayList<Callable<Object>>();
        for(var a:actors) tasks.add(() -> { try { return create(a); } catch(BusinessException e) { return e.getErrorCode(); } });
        return race(tasks);
    }
    private void rows(long expected) {
        for(String table:List.of("appointment_orders","payment_orders","appointment_active_claims","appointment_create_requests"))
            assertThat(count(table)).as(table).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules WHERE doctor_id=?",Long.class,doctor)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointment_orders a
                JOIN payment_orders p ON p.appointment_order_id=a.id
                JOIN appointment_active_claims c ON c.appointment_order_id=a.id
                JOIN appointment_create_requests r ON r.appointment_order_id=a.id
                WHERE a.fee_amount=p.amount AND a.fee_currency=p.currency
                AND a.user_id=p.user_id AND a.user_id=r.user_id AND a.schedule_id=c.schedule_id
                """, Long.class)).isEqualTo(expected);
    }
    @Test void concurrentFirstBookingsGenerateOnlyOneSession() throws Exception {
        assertThat(compete(actors())).allMatch(CreateAppointmentResponse.class::isInstance);
        rows(4); inventory(20,16,4,0);
    }
    @Test void onlyOneRequestCanTakeLastPlace() throws Exception {
        jdbc.update("UPDATE booking_settings SET capacity_per_session=1 WHERE id=1");
        var results=compete(actors());
        assertThat(results.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(x -> !(x instanceof CreateAppointmentResponse)).toList()).containsOnly(ErrorCode.SCHEDULE_UNAVAILABLE);
        rows(1); inventory(1,0,1,0);
    }
    @Test void concurrentSameKeyReplaysCommittedReceipt() throws Exception {
        var a=actor("110105199001010010"); var key=UUID.randomUUID().toString();
        List<Callable<AppointmentCreationResult>> tasks=new ArrayList<>();
        for(int i=0;i<4;i++) tasks.add(() -> bookings.create(a.user(),key,request(a)));
        var results=race(tasks);
        assertThat(results.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(1);
        var ids=results.stream().map(r -> r instanceof CreateAppointmentResponse c ? c.getAppointmentOrderId():((ReplayedAppointmentResponse)r).getAppointmentOrderId()).toList();
        assertThat(new HashSet<>(ids)).hasSize(1); rows(1); inventory(20,19,1,0);
    }
    @Test void sameDocumentAcrossAccountsHasOnlyOneActiveClaim() throws Exception {
        var results=compete(List.of(actor("110105199001010010"),actor("110105199001010010")));
        assertThat(results.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(x -> !(x instanceof CreateAppointmentResponse)).toList()).containsExactly(ErrorCode.DUPLICATE_ACTIVE_APPOINTMENT);
        rows(1); inventory(20,19,1,0);
    }
}
