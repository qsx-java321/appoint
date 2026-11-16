package com.qsx.appoint.support;

import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 类独占MySQL；业务事务真实提交，工作线程结束后再清理夹具。 */
@MySqlIntegrationTest
public abstract class MySqlBusinessTestSupport {
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected BookingCoordinator bookings;
    @Autowired protected MutableAppointmentClock clock;
    @Autowired protected FormalPaymentTestSupport payments;
    @Autowired private UserMapper users;
    @Autowired private PatientMapper patients;
    @Autowired private DoctorMapper doctors;
    @Autowired private MySQLContainer<?> mysql;
    private CommittedFixtureScope scope;
    protected long doctor;
    private boolean workersTerminated = true;

    @BeforeEach
    void prepare() throws Exception {
        try (var c = jdbc.getDataSource().getConnection()) {
            assertThat(c.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(c.getMetaData().getURL()).startsWith(mysql.getJdbcUrl().split("\\?")[0]);
            assertThat(c.getCatalog()).isEqualTo("appoint_it");
        }
        clock.set("2026-11-05T12:00:00");
        scope = new CommittedFixtureScope(jdbc);
        doctor = BookingTestDoctors.copyOf(doctors, 1L);
    }
    @AfterEach void cleanup() {
        // 超时且线程仍存活时交由DirtiesContext销毁整库，避免清理与业务写入竞争。
        if (scope != null && workersTerminated) scope.close();
        clock.set("2026-11-05T12:00:00");
    }
    protected record Actor(long user, long patient) {}
    protected Actor actor(String document) {
        var u = new User(); u.setEmail(UUID.randomUUID()+"@example.com"); u.setPasswordHash("unused"); users.insert(u);
        var p = new Patient(); p.setUserId(u.getId()); p.setName("测试就诊人"); p.setDocumentType("RESIDENT_ID_CARD");
        p.setDocumentNo(document); p.setContactPhone("13800138000"); p.setContactEmail("patient@example.com"); p.setEnabled(true); patients.insert(p);
        return new Actor(u.getId(), p.getId());
    }
    protected CreateAppointmentRequest request(Actor a) {
        var r = new CreateAppointmentRequest(); r.setDoctorId(doctor); r.setPatientId(a.patient());
        r.setAppointmentDate(LocalDate.of(2026,11,7)); r.setSessionType("MORNING_FIRST"); return r;
    }
    protected CreateAppointmentResponse create(Actor a) {
        return (CreateAppointmentResponse) bookings.create(a.user(), UUID.randomUUID().toString(), request(a));
    }
    protected long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table, Long.class); }
    protected void inventory(int total, int available, int held, int confirmed) {
        var row = jdbc.queryForMap("SELECT total_capacity,available_capacity,held_capacity,confirmed_capacity FROM schedules WHERE doctor_id=?",doctor);
        assertThat(row.get("total_capacity")).isEqualTo(total);
        assertThat(row.get("available_capacity")).isEqualTo(available);
        assertThat(row.get("held_capacity")).isEqualTo(held);
        assertThat(row.get("confirmed_capacity")).isEqualTo(confirmed);
        assertThat(available+held+confirmed).isEqualTo(total);
        assertThat(Math.min(available,Math.min(held,confirmed))).isGreaterThanOrEqualTo(0);
    }
    protected <T> List<T> race(List<Callable<T>> tasks) throws Exception {
        var ready = new CountDownLatch(tasks.size()); var start = new CountDownLatch(1);
        var executor = new TestExecutor(tasks.size());
        try (executor) {
            var futures = new ArrayList<Future<T>>();
            for (var task : tasks) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10,TimeUnit.SECONDS)) throw new AssertionError("启动屏障超时");
                return task.call();
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(30,TimeUnit.SECONDS));
            return results;
        } finally {
            workersTerminated = executor.isTerminated();
        }
    }
}
