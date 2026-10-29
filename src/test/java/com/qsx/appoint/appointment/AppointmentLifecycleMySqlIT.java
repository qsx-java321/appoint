package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.dto.ReplayedAppointmentResponse;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.catalog.service.ScheduleManagementService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.service.PaymentService;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.patient.dto.CreatePatientRequest;
import com.qsx.appoint.patient.dto.UpdatePatientRequest;
import com.qsx.appoint.patient.dto.PatientResponse;
import com.qsx.appoint.patient.service.PatientService;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "appoint.expiration.enabled=false")
class AppointmentLifecycleMySqlIT {
    private static final AtomicInteger SCHEDULE_SEQUENCE = new AtomicInteger();
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
    @Autowired private ScheduleManagementService scheduleManagementService;
    @Autowired private UserMapper userMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private PatientService patientService;

    @Test
    void paymentAndCancellationProduceOneConsistentTerminalState() throws Exception {
        Long userId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);

        List<Object> outcomes = race(
                () -> paymentService.simulateSuccess(userId, created.getPaymentOrderId()),
                () -> appointmentService.cancel(userId, created.getAppointmentOrderId()));

        assertThat(outcomes).hasSize(2);
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        PaymentOrder payment = paymentOrderMapper.selectById(created.getPaymentOrderId());
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if ("CONFIRMED".equals(appointment.getStatus())) {
            assertThat(payment.getStatus()).isEqualTo("PAID");
            assertInventory(schedule, 1, 0, 0, 1);
        } else {
            assertThat(appointment.getStatus()).isEqualTo("CANCELLED");
            assertThat(payment.getStatus()).isIn("CLOSED", "REFUNDED");
            assertInventory(schedule, 1, 1, 0, 0);
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
    void twentyUsersCompetingForLastThreePlacesNeverOversell() throws Exception {
        Long scheduleId = createSchedule(3);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(20)) {
            List<Future<Object>> futures = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        Long userId = createUser();
                        Long patientId = createPatient(userId, "990000%012d".formatted(index));
                        try {
                            return createAppointment(userId, patientId, scheduleId, UUID.randomUUID().toString());
                        } catch (BusinessException exception) {
                            return exception;
                        }
                    }))
                    .toList();
            ready.await();
            start.countDown();
            List<Object> outcomes = new java.util.ArrayList<>();
            for (Future<Object> future : futures) outcomes.add(future.get());

            assertThat(outcomes.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(3);
            assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(17);
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        assertInventory(schedule, 3, 0, 3, 0);
        assertThat(appointmentOrderMapper.selectList(null).stream()
                .filter(order -> scheduleId.equals(order.getScheduleId()))
                .filter(order -> "PENDING_PAYMENT".equals(order.getStatus())))
                .hasSize(3);
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
        assertInventory(scheduleMapper.selectById(scheduleId), 1, 1, 0, 0);
    }

    @Test
    void paymentRacingScheduleClosureKeepsOneConsistentInventoryState() throws Exception {
        Long userId = createUser();
        Long operatorId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);

        race(
                () -> paymentService.simulateSuccess(userId, created.getPaymentOrderId()),
                () -> scheduleManagementService.close(operatorId, scheduleId));

        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        PaymentOrder payment = paymentOrderMapper.selectById(created.getPaymentOrderId());
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        assertThat(schedule.getStatus()).isEqualTo("CLOSED");
        if ("CONFIRMED".equals(appointment.getStatus())) {
            assertThat(payment.getStatus()).isEqualTo("PAID");
            assertThat(schedule.getAvailableCapacity()).isZero();
            assertThat(schedule.getHeldCapacity()).isZero();
            assertThat(schedule.getConfirmedCapacity()).isEqualTo(1);
        } else {
            assertThat(appointment.getStatus()).isEqualTo("CANCELLED_BY_HOSPITAL");
            assertThat(payment.getStatus()).isEqualTo("CLOSED");
            assertThat(schedule.getAvailableCapacity()).isEqualTo(1);
            assertThat(schedule.getHeldCapacity()).isZero();
            assertThat(schedule.getConfirmedCapacity()).isZero();
        }
        assertThat(schedule.getAvailableCapacity() + schedule.getHeldCapacity()
                + schedule.getConfirmedCapacity()).isEqualTo(schedule.getTotalCapacity());
    }

    @Test
    void concurrentSameIdempotencyKeyReplaysOneCommittedResponse() throws Exception {
        Long userId = createUser();
        Long patientId = createPatient(userId);
        Long scheduleId = createSchedule();
        String key = UUID.randomUUID().toString();

        List<Object> outcomes = race(
                () -> createAppointmentResult(userId, patientId, scheduleId, key),
                () -> createAppointmentResult(userId, patientId, scheduleId, key));

        CreateAppointmentResponse created = (CreateAppointmentResponse) outcomes.stream()
                .filter(CreateAppointmentResponse.class::isInstance).findFirst().orElseThrow();
        ReplayedAppointmentResponse replayed = (ReplayedAppointmentResponse) outcomes.stream()
                .filter(ReplayedAppointmentResponse.class::isInstance).findFirst().orElseThrow();
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        assertThat(replayed.getAppointmentOrderId()).isEqualTo(created.getAppointmentOrderId());
        assertThat(replayed.getAppointmentNo()).isEqualTo(appointment.getAppointmentNo());
        assertThat(replayed.isReplayed()).isTrue();
        assertThat(appointmentOrderMapper.selectList(null)).filteredOn(order -> order.getUserId().equals(userId))
                .hasSize(1);
    }

    @Test
    void concurrentDifferentPayloadsWithSameKeyYieldOneConflict() throws Exception {
        Long userId = createUser();
        Long patientId = createPatient(userId);
        Long firstScheduleId = createSchedule();
        Long secondScheduleId = createSchedule();
        String key = UUID.randomUUID().toString();

        List<Object> outcomes = race(
                () -> createAppointmentResult(userId, patientId, firstScheduleId, key),
                () -> createAppointmentResult(userId, patientId, secondScheduleId, key));

        assertThat(outcomes.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(1);
        BusinessException conflict = (BusinessException) outcomes.stream()
                .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
        assertThat(conflict.getErrorCode()).isEqualTo(com.qsx.appoint.common.ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void concurrentDuplicatePatientDocumentIsRejectedByDatabaseConstraint() throws Exception {
        Long userId = createUser();
        CreatePatientRequest request = patientRequest("并发就诊人");

        List<Object> outcomes = race(
                () -> patientService.create(userId, request),
                () -> patientService.create(userId, request));

        assertThat(outcomes.stream().filter(PatientResponse.class::isInstance).count()).isEqualTo(1);
        BusinessException duplicate = (BusinessException) outcomes.stream()
                .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
        assertThat(duplicate.getErrorCode()).isEqualTo(com.qsx.appoint.common.ErrorCode.PATIENT_DOCUMENT_EXISTS);
    }

    @Test
    void patientUpdateAndAppointmentCreationSerializeToACompleteSnapshot() throws Exception {
        Long userId = createUser();
        Long patientId = createPatient(userId);
        Long scheduleId = createSchedule();
        UpdatePatientRequest update = new UpdatePatientRequest();
        update.setName("修改后姓名");

        List<Object> outcomes = race(
                () -> createAppointment(userId, patientId, scheduleId, UUID.randomUUID().toString()),
                () -> patientService.updateOwned(userId, patientId, update));

        assertThat(outcomes).anyMatch(CreateAppointmentResponse.class::isInstance);
        assertThat(outcomes).anyMatch(PatientResponse.class::isInstance);
        CreateAppointmentResponse created = (CreateAppointmentResponse) outcomes.stream()
                .filter(CreateAppointmentResponse.class::isInstance).findFirst().orElseThrow();
        AppointmentOrder snapshot = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        assertThat(snapshot.getPatientName()).isIn("MySQL 就诊人", "修改后姓名");
        assertThat(snapshot.getPatientDocumentNo()).isEqualTo("110105199001010010");
        assertThat(snapshot.getPatientContactPhone()).isEqualTo("13800138000");
        assertThat(patientMapper.selectById(patientId).getName()).isEqualTo("修改后姓名");
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
        return createSchedule(1);
    }

    private Long createSchedule(int capacity) {
        int sequence = SCHEDULE_SEQUENCE.getAndIncrement();
        Schedule schedule = new Schedule();
        schedule.setDoctorId(1L + (sequence % 4) / 2);
        schedule.setAppointmentDate(LocalDate.now().plusDays(2L + sequence / 4));
        boolean morning = sequence % 2 == 0;
        schedule.setSessionType(morning ? "MORNING" : "AFTERNOON");
        schedule.setStartTime(morning ? LocalTime.of(8, 0) : LocalTime.of(14, 0));
        schedule.setEndTime(morning ? LocalTime.of(12, 0) : LocalTime.of(17, 0));
        schedule.setStatus("OPEN");
        schedule.setTotalCapacity(capacity);
        schedule.setAvailableCapacity(capacity);
        schedule.setHeldCapacity(0);
        schedule.setConfirmedCapacity(0);
        schedule.setNextVisitNumber(1);
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }

    private CreateAppointmentResponse createAppointment(Long userId, Long scheduleId) {
        return createAppointment(userId, createPatient(userId), scheduleId, UUID.randomUUID().toString());
    }

    private CreateAppointmentResponse createAppointment(Long userId, Long patientId, Long scheduleId, String key) {
        AppointmentCreationResult result = createAppointmentResult(userId, patientId, scheduleId, key);
        assertThat(result).isInstanceOf(CreateAppointmentResponse.class);
        return (CreateAppointmentResponse) result;
    }

    private AppointmentCreationResult createAppointmentResult(Long userId, Long patientId, Long scheduleId, String key) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setScheduleId(scheduleId);
        request.setPatientId(patientId);
        return appointmentService.create(userId, key, request);
    }

    private Long createPatient(Long userId) {
        return createPatient(userId, "110105199001010010");
    }

    private Long createPatient(Long userId, String documentNo) {
        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName("MySQL 就诊人");
        patient.setDocumentType("RESIDENT_ID_CARD");
        patient.setDocumentNo(documentNo);
        patient.setContactPhone("13800138000");
        patient.setContactEmail("patient@example.com");
        patient.setEnabled(true);
        patientMapper.insert(patient);
        return patient.getId();
    }

    private void assertInventory(Schedule schedule, int total, int available, int held, int confirmed) {
        assertThat(schedule.getStatus()).isEqualTo("OPEN");
        assertThat(schedule.getTotalCapacity()).isEqualTo(total);
        assertThat(schedule.getAvailableCapacity()).isEqualTo(available);
        assertThat(schedule.getHeldCapacity()).isEqualTo(held);
        assertThat(schedule.getConfirmedCapacity()).isEqualTo(confirmed);
        assertThat(available + held + confirmed).isEqualTo(total);
    }

    private CreatePatientRequest patientRequest(String name) {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setName(name);
        request.setDocumentType("RESIDENT_ID_CARD");
        request.setDocumentNo("110105199001010010");
        request.setContactPhone("13800138000");
        request.setContactEmail("patient@example.com");
        return request;
    }
}
