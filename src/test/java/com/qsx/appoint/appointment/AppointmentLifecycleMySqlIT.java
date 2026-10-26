package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.service.PaymentService;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "appoint.expiration.enabled=false")
class AppointmentLifecycleMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appoint")
            .withUsername("appoint")
            .withPassword("appoint-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired private AppointmentService appointmentService;
    @Autowired private PaymentService paymentService;
    @Autowired private AppointmentLifecycleService lifecycleService;
    @Autowired private AppointmentOrderMapper appointmentOrderMapper;
    @Autowired private PaymentOrderMapper paymentOrderMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private UserMapper userMapper;

    @Test
    void paymentAndCancellationProduceOneConsistentTerminalState() throws Exception {
        Long userId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);

        List<Object> outcomes = race(
                () -> paymentService.simulateSuccess(userId, created.getPaymentOrderId()),
                () -> appointmentService.cancel(userId, created.getAppointmentOrderId()));

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(value -> !(value instanceof BusinessException)).count()).isEqualTo(1);
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        PaymentOrder payment = paymentOrderMapper.selectById(created.getPaymentOrderId());
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if ("CONFIRMED".equals(appointment.getStatus())) {
            assertThat(payment.getStatus()).isEqualTo("PAID");
            assertThat(schedule.getStatus()).isEqualTo("BOOKED");
        } else {
            assertThat(appointment.getStatus()).isEqualTo("CANCELLED");
            assertThat(payment.getStatus()).isEqualTo("CLOSED");
            assertThat(schedule.getStatus()).isEqualTo("AVAILABLE");
        }
    }

    @Test
    void concurrentDuplicatePaymentsReturnTheSameSuccess() throws Exception {
        Long userId = createUser();
        CreateAppointmentResponse created = createAppointment(userId, createSchedule());

        List<Object> outcomes = race(
                () -> paymentService.simulateSuccess(userId, created.getPaymentOrderId()),
                () -> paymentService.simulateSuccess(userId, created.getPaymentOrderId()));

        assertThat(outcomes).noneMatch(BusinessException.class::isInstance);
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getStatus()).isEqualTo("CONFIRMED");
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getStatus()).isEqualTo("PAID");
    }

    @Test
    void concurrentUsersCompetingForOneScheduleProduceOnlyOneActiveAppointment() throws Exception {
        Long firstUserId = createUser();
        Long secondUserId = createUser();
        Long scheduleId = createSchedule();

        List<Object> outcomes = race(
                () -> createAppointment(firstUserId, scheduleId),
                () -> createAppointment(secondUserId, scheduleId));

        assertThat(outcomes.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
        BusinessException rejected = (BusinessException) outcomes.stream()
                .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
        assertThat(rejected.getErrorCode()).isEqualTo(com.qsx.appoint.common.ErrorCode.SCHEDULE_UNAVAILABLE);
        assertThat(scheduleMapper.selectById(scheduleId).getStatus()).isEqualTo("HELD");
    }

    @Test
    void paymentRacingExpirationProducesOneExpiredTerminalStateAfterDeadline() throws Exception {
        Long userId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        appointment.setPaymentExpiresAt(LocalDateTime.now().minusSeconds(1));
        appointmentOrderMapper.updateById(appointment);

        List<Object> outcomes = race(
                () -> paymentService.simulateSuccess(userId, created.getPaymentOrderId()),
                () -> lifecycleService.expireIfDue(created.getAppointmentOrderId()));

        assertThat(outcomes).anyMatch(value -> Boolean.TRUE.equals(value));
        assertThat(outcomes).anyMatch(BusinessException.class::isInstance);
        BusinessException rejected = (BusinessException) outcomes.stream()
                .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
        assertThat(rejected.getErrorCode()).isEqualTo(com.qsx.appoint.common.ErrorCode.PAYMENT_EXPIRED);
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getStatus()).isEqualTo("EXPIRED");
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getStatus()).isEqualTo("CLOSED");
        assertThat(scheduleMapper.selectById(scheduleId).getStatus()).isEqualTo("AVAILABLE");
    }

    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> firstResult = executor.submit(awaitStart(first, ready, start));
            Future<Object> secondResult = executor.submit(awaitStart(second, ready, start));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        }
    }

    private Callable<Object> awaitStart(Callable<Object> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                return action.call();
            } catch (BusinessException exception) {
                return exception;
            }
        };
    }

    private Long createUser() {
        User user = new User();
        user.setEmail("mysql-it-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("unused-in-service-test");
        userMapper.insert(user);
        return user.getId();
    }

    private Long createSchedule() {
        Schedule schedule = new Schedule();
        schedule.setDoctorId(1L);
        schedule.setAppointmentDate(LocalDate.of(2099, 6, 1));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(9, 30));
        schedule.setStatus("AVAILABLE");
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }

    private CreateAppointmentResponse createAppointment(Long userId, Long scheduleId) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setScheduleId(scheduleId);
        return appointmentService.create(userId, request);
    }
}
