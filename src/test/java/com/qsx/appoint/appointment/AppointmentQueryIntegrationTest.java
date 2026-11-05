package com.qsx.appoint.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.entity.Doctor;
import com.qsx.appoint.catalog.entity.Hospital;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.catalog.mapper.HospitalMapper;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentRefundMapper;
import com.qsx.appoint.payment.entity.PaymentRefund;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证预约读取会返回支付信息，并安全处理异常关联数据。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AppointmentQueryIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private DoctorMapper doctorMapper;
    @Autowired private HospitalMapper hospitalMapper;
    @Autowired private PaymentOrderMapper paymentOrderMapper;
    @Autowired private PaymentRefundMapper paymentRefundMapper;
    @Autowired private AppointmentOrderMapper appointmentOrderMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void appointmentQueriesExposePaymentBeforeAndAfterPayment() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment appointment = createAppointment(user, createAvailableSchedule().getId());

        assertAppointmentDetail(user, appointment, "PENDING_PAYMENT", "UNPAID");
        assertAppointmentList(user, appointment, "PENDING_PAYMENT", "UNPAID");

        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", appointment.paymentOrderId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        assertAppointmentDetail(user, appointment, "CONFIRMED", "PAID");
        assertAppointmentList(user, appointment, "CONFIRMED", "PAID");
    }

    @Test
    void paidAppointmentWaitingForEventIsShownAsConfirming() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment appointment = createAppointment(user, createAvailableSchedule().getId());
        PaymentOrder payment = paymentOrderMapper.selectById(appointment.paymentOrderId());
        payment.setStatus("PAID");
        paymentOrderMapper.updateById(payment);

        mockMvc.perform(get("/api/v1/appointments/{id}", appointment.appointmentOrderId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowStatus").value("CONFIRMING_APPOINTMENT"));
    }

    @Test
    void queryShowsAmountAndLatestProcessingAttemptSummary() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment appointment = createAppointment(user, createAvailableSchedule().getId());

        mockMvc.perform(post("/api/v1/payments/{id}/attempts", appointment.paymentOrderId())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"MOCK\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/appointments/{id}", appointment.appointmentOrderId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(50.00))
                .andExpect(jsonPath("$.data.currency").value("CNY"))
                .andExpect(jsonPath("$.data.workflowStatus").value("PAYMENT_PROCESSING"))
                .andExpect(jsonPath("$.data.paymentAttemptCount").value(1))
                .andExpect(jsonPath("$.data.latestPaymentAttempt.id").isString())
                .andExpect(jsonPath("$.data.latestPaymentAttempt.channel").value("MOCK"))
                .andExpect(jsonPath("$.data.latestPaymentAttempt.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.refund").doesNotExist())
                .andExpect(jsonPath("$.data.canCancel").value(true));
    }

    @Test
    void queryShowsRefundingAndRefundedSummaries() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment appointment = createAppointment(user, createAvailableSchedule().getId());
        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", appointment.paymentOrderId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", appointment.appointmentOrderId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.workflowStatus").value("CANCELLED_REFUNDING"))
                .andExpect(jsonPath("$.data.refund.status").value("PENDING"))
                .andExpect(jsonPath("$.data.refund.amount").value(50.00))
                .andExpect(jsonPath("$.data.refund.currency").value("CNY"));

        PaymentRefund refund = paymentRefundMapper.selectByPaymentOrderIdForUpdate(appointment.paymentOrderId());
        refund.setStatus("SUCCEEDED");
        refund.setCompletedAt(java.time.LocalDateTime.now());
        paymentRefundMapper.updateById(refund);
        PaymentOrder payment = paymentOrderMapper.selectById(appointment.paymentOrderId());
        payment.setStatus("REFUNDED");
        paymentOrderMapper.updateById(payment);

        mockMvc.perform(get("/api/v1/appointments/{id}", appointment.appointmentOrderId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowStatus").value("CANCELLED_REFUNDED"))
                .andExpect(jsonPath("$.data.refund.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.refund.completedAt").isNotEmpty());
    }

    @Test
    void legacyTerminalOrderWithNullFeeRemainsReadable() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment appointment = createAppointment(user, createAvailableSchedule().getId());
        AppointmentOrder order = appointmentOrderMapper.selectById(appointment.appointmentOrderId());
        order.setStatus("CANCELLED");
        order.setClosedAt(java.time.LocalDateTime.now());
        appointmentOrderMapper.updateById(order);
        jdbcTemplate.update("UPDATE payment_orders SET status='CLOSED', legacy=TRUE, amount=NULL, currency=NULL WHERE id=?",
                appointment.paymentOrderId());

        mockMvc.perform(get("/api/v1/appointments/{id}", appointment.appointmentOrderId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.amount").doesNotExist())
                .andExpect(jsonPath("$.data.currency").doesNotExist());
    }

    @Test
    void anotherUserCannotReadAppointment() throws Exception {
        AuthenticatedUser owner = registerAndLogin();
        CreatedAppointment appointment = createAppointment(owner, createAvailableSchedule().getId());
        AuthenticatedUser otherUser = registerAndLogin();

        mockMvc.perform(get("/api/v1/appointments/{id}", appointment.appointmentOrderId())
                        .header("Authorization", bearer(otherUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    void emptyAppointmentListReturnsEmptyArray() throws Exception {
        AuthenticatedUser user = registerAndLogin();

        mockMvc.perform(get("/api/v1/appointments/my").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void appointmentKeepsDoctorSnapshotAfterCatalogChanges() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment appointment = createAppointment(user, createAvailableSchedule().getId());

        Doctor doctor = doctorMapper.selectById(1L);
        doctor.setDoctorNo("DOC999999");
        doctor.setName("已改名医生");
        doctor.setTitle("已调整职称");
        doctorMapper.updateById(doctor);
        Hospital hospital = hospitalMapper.selectById(1L);
        hospital.setName("已改名医院");
        hospital.setAddress("已变更地址 99 号");
        hospitalMapper.updateById(hospital);
        assertAppointmentDetail(user, appointment, "PENDING_PAYMENT", "UNPAID");
        assertAppointmentList(user, appointment, "PENDING_PAYMENT", "UNPAID");
    }

    @Test
    void appointmentQueriesFailWhenPaymentIsMissing() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment appointment = createAppointment(user, createAvailableSchedule().getId());
        paymentOrderMapper.deleteById(appointment.paymentOrderId());

        assertInternalErrorForDetailAndList(user, appointment.appointmentOrderId());
        assertThat(paymentOrderMapper.selectById(appointment.paymentOrderId())).isNull();
    }

    @Test
    void appointmentQueriesFailWhenPaymentBelongsToAnotherUser() throws Exception {
        AuthenticatedUser owner = registerAndLogin();
        CreatedAppointment appointment = createAppointment(owner, createAvailableSchedule().getId());
        AuthenticatedUser anotherUser = registerAndLogin();
        PaymentOrder payment = paymentOrderMapper.selectById(appointment.paymentOrderId());
        payment.setUserId(anotherUser.id());
        paymentOrderMapper.updateById(payment);

        assertInternalErrorForDetailAndList(owner, appointment.appointmentOrderId());
        assertThat(paymentOrderMapper.selectById(appointment.paymentOrderId()).getUserId()).isEqualTo(anotherUser.id());
    }

    private void assertAppointmentDetail(AuthenticatedUser user, CreatedAppointment appointment,
                                         String appointmentStatus, String paymentStatus) throws Exception {
        mockMvc.perform(get("/api/v1/appointments/{id}", appointment.appointmentOrderId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(appointment.appointmentOrderId().toString()))
                .andExpect(jsonPath("$.data.status").value(appointmentStatus))
                .andExpect(jsonPath("$.data.paymentOrderId").value(appointment.paymentOrderId().toString()))
                .andExpect(jsonPath("$.data.paymentStatus").value(paymentStatus))
                .andExpect(jsonPath("$.data.amount").value(50.00))
                .andExpect(jsonPath("$.data.currency").value("CNY"))
                .andExpect(jsonPath("$.data.paymentAttemptCount").isNumber())
                .andExpect(jsonPath("$.data.workflowStatus").value(
                        "CONFIRMED".equals(appointmentStatus) ? "CONFIRMED" : "WAITING_FOR_PAYMENT"))
                .andExpect(jsonPath("$.data.appointmentDate").value(LocalDate.now().plusDays(6).toString()))
                .andExpect(jsonPath("$.data.startTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("10:00:00"))
                .andExpect(jsonPath("$.data.paymentExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.data.doctor.id").value("1"))
                .andExpect(jsonPath("$.data.doctor.doctorNo").value("DOC000001"))
                .andExpect(jsonPath("$.data.doctor.name").value("张医生"))
                .andExpect(jsonPath("$.data.doctor.title").value("主任医师"))
                .andExpect(jsonPath("$.data.doctor.department.id").value("1"))
                .andExpect(jsonPath("$.data.doctor.department.name").value("内科"))
                .andExpect(jsonPath("$.data.doctor.hospital.id").value("1"))
                .andExpect(jsonPath("$.data.doctor.hospital.name").value("示例市第一人民医院"))
                .andExpect(jsonPath("$.data.doctor.hospital.address").value("示例市健康路 1 号"));
    }

    private void assertAppointmentList(AuthenticatedUser user, CreatedAppointment appointment,
                                       String appointmentStatus, String paymentStatus) throws Exception {
        mockMvc.perform(get("/api/v1/appointments/my").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(appointment.appointmentOrderId().toString()))
                .andExpect(jsonPath("$.data[0].status").value(appointmentStatus))
                .andExpect(jsonPath("$.data[0].paymentOrderId").value(appointment.paymentOrderId().toString()))
                .andExpect(jsonPath("$.data[0].paymentStatus").value(paymentStatus))
                .andExpect(jsonPath("$.data[0].workflowStatus").value(
                        "CONFIRMED".equals(appointmentStatus) ? "CONFIRMED" : "WAITING_FOR_PAYMENT"))
                .andExpect(jsonPath("$.data[0].appointmentDate").value(LocalDate.now().plusDays(6).toString()))
                .andExpect(jsonPath("$.data[0].startTime").value("08:00:00"))
                .andExpect(jsonPath("$.data[0].endTime").value("10:00:00"))
                .andExpect(jsonPath("$.data[0].doctor.id").value("1"))
                .andExpect(jsonPath("$.data[0].doctor.doctorNo").value("DOC000001"))
                .andExpect(jsonPath("$.data[0].doctor.name").value("张医生"))
                .andExpect(jsonPath("$.data[0].doctor.department.name").value("内科"))
                .andExpect(jsonPath("$.data[0].doctor.hospital.name").value("示例市第一人民医院"))
                .andExpect(jsonPath("$.data[0].doctor.hospital.address").value("示例市健康路 1 号"));
    }

    private void assertInternalErrorForDetailAndList(AuthenticatedUser user, Long appointmentOrderId) throws Exception {
        mockMvc.perform(get("/api/v1/appointments/{id}", appointmentOrderId)
                        .header("Authorization", bearer(user)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/appointments/my").header("Authorization", bearer(user)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private AuthenticatedUser registerAndLogin() throws Exception {
        String email = "appointment-query-" + UUID.randomUUID() + "@example.com";
        String requestBody = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(registerResponse).at("/data/id").asLong();

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).at("/data/accessToken").asText();
        String patientResponse = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"查询就诊人\",\"documentType\":\"RESIDENT_ID_CARD\"," +
                                "\"documentNo\":\"110105199001010010\",\"contactPhone\":\"13800138000\"," +
                                "\"contactEmail\":\"patient@example.com\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new AuthenticatedUser(userId, token,
                objectMapper.readTree(patientResponse).at("/data/id").asLong());
    }

    private CreatedAppointment createAppointment(AuthenticatedUser user, Long scheduleId) throws Exception {
        Schedule selected = scheduleMapper.selectById(scheduleId);
        String response = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":" + selected.getDoctorId()
                                + ",\"appointmentDate\":\"" + selected.getAppointmentDate()
                                + "\",\"sessionType\":\"" + selected.getSessionType()
                                + "\",\"patientId\":" + user.patientId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).get("data");
        return new CreatedAppointment(data.get("appointmentOrderId").asLong(), data.get("paymentOrderId").asLong(), scheduleId);
    }

    private Schedule createAvailableSchedule() {
        Schedule schedule = new Schedule();
        schedule.setDoctorId(1L);
        schedule.setAppointmentDate(LocalDate.now().plusDays(6));
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setStatus("OPEN");
        schedule.setSessionType("MORNING_FIRST");
        schedule.setTotalCapacity(5);
        schedule.setRegistrationFee(new java.math.BigDecimal("50.00"));
        schedule.setFeeCurrency("CNY");
        schedule.setAvailableCapacity(5);
        schedule.setHeldCapacity(0);
        schedule.setConfirmedCapacity(0);
        schedule.setNextVisitNumber(1);
        scheduleMapper.insert(schedule);
        return schedule;
    }

    private String bearer(AuthenticatedUser user) {
        return "Bearer " + user.token();
    }

    private record AuthenticatedUser(Long id, String token, Long patientId) { }
    private record CreatedAppointment(Long appointmentOrderId, Long paymentOrderId, Long scheduleId) { }
}
