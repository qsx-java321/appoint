package com.qsx.appoint.mysql;

import com.qsx.appoint.support.MySqlBusinessTestSupport;
import com.qsx.appoint.support.TestExecutor;
import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.catalog.model.AppointmentSession;
import com.qsx.appoint.common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** 多个独立业务事务竞争真实唯一键和行锁；SQL异常直接使测试失败。 */
class BookingConcurrencyMySqlIT extends MySqlBusinessTestSupport {
    @Autowired private ScheduleMapper schedules;
    @Autowired private TransactionTemplate transaction;

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
    @ParameterizedTest(name = "已有场次={0}")
    @ValueSource(booleans = {false, true})
    void onlyOneRequestCanTakeLastPlace(boolean existingSession) throws Exception {
        jdbc.update("UPDATE booking_settings SET capacity_per_session=1 WHERE id=1");
        var participants = actors();
        if (existingSession) generate(request(participants.getFirst()));

        var results=compete(participants);
        assertThat(results.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(x -> !(x instanceof CreateAppointmentResponse)).toList()).containsOnly(ErrorCode.SCHEDULE_UNAVAILABLE);
        rows(1); inventory(1,0,1,0);
    }

    @ParameterizedTest(name = "已有场次={0}")
    @ValueSource(booleans = {true, false})
    void distinctSessionsCanCommitWhileTheFirstBookingIsOpen(boolean existingSessions) throws Exception {
        var first = actor("110105199001010010");
        var second = actor("110105199002020026");
        var firstRequest = request(first);
        var secondRequest = request(second);
        secondRequest.setSessionType(AppointmentSession.MORNING_SECOND.name());
        if (existingSessions) {
            generate(firstRequest);
            generate(secondRequest);
        }

        var firstReady = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        try (var executor = new TestExecutor(2)) {
            var firstBooking = executor.submit(() -> transaction.executeWithoutResult(status -> {
                assertThat(bookings.create(first.user(), UUID.randomUUID().toString(), firstRequest))
                        .isInstanceOf(CreateAppointmentResponse.class);
                firstReady.countDown();
                await(releaseFirst);
            }));
            try {
                await(firstReady);
                var secondBooking = executor.submit(() -> bookings.create(
                        second.user(), UUID.randomUUID().toString(), secondRequest));
                assertThat(secondBooking.get(10, TimeUnit.SECONDS)).isInstanceOf(CreateAppointmentResponse.class);
                assertThat(ordersFor(first.user())).isZero();
                assertThat(ordersFor(second.user())).isEqualTo(1);
            } finally {
                releaseFirst.countDown();
            }
            firstBooking.get(10, TimeUnit.SECONDS);
        }

        assertThat(ordersFor(first.user())).isEqualTo(1);
        assertThat(ordersFor(second.user())).isEqualTo(1);
        assertThat(count("payment_orders")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules WHERE doctor_id=?", Long.class, doctor))
                .isEqualTo(2);
        assertSingleHold(firstRequest);
        assertSingleHold(secondRequest);
    }

    private void generate(CreateAppointmentRequest request) {
        var session = AppointmentSession.valueOf(request.getSessionType());
        schedules.insertGeneratedFromSettings(doctor, request.getAppointmentDate(),
                session.startTime(), session.endTime(), session.name());
    }

    private void assertSingleHold(CreateAppointmentRequest request) {
        var schedule = schedules.selectByDoctorDateSession(doctor, request.getAppointmentDate(),
                request.getSessionType());
        assertThat(schedule.getTotalCapacity()).isEqualTo(20);
        assertThat(schedule.getAvailableCapacity()).isEqualTo(19);
        assertThat(schedule.getHeldCapacity()).isEqualTo(1);
        assertThat(schedule.getConfirmedCapacity()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_orders WHERE schedule_id=?",
                Long.class, schedule.getId())).isEqualTo(1);
    }

    private long ordersFor(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM appointment_orders WHERE user_id=?", Long.class, userId);
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待并发下单测试信号时被中断", exception);
        }
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
