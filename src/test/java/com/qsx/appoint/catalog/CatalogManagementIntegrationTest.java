package com.qsx.appoint.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.dto.CreateScheduleRequest;
import com.qsx.appoint.catalog.dto.AdminDepartmentResponse;
import com.qsx.appoint.catalog.dto.CreateDepartmentRequest;
import com.qsx.appoint.catalog.service.CatalogManagementService;
import com.qsx.appoint.catalog.service.ScheduleManagementService;
import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.AppointmentResponse;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.qsx.appoint.support.TestExecutor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spring/H2验证目录管理和预约快照；普通用例回滚，并发用例真实提交后在finally清理随机科室。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogManagementIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate fixtureJdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CatalogManagementService catalogManagementService;
    @Autowired private ScheduleManagementService scheduleManagementService;
    @Autowired private BookingCoordinator bookingCoordinator;
    @Autowired private AppointmentService appointmentService;
    @Autowired private UserMapper userMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private Clock appointmentClock;

    @Test
    void adminCanCreateHospitalThatImmediatelyAppearsInCatalog() throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/hospitals")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " 示例市第二人民医院 ",
                                  "address": " 示例市健康路 20 号 "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("示例市第二人民医院"))
                .andExpect(jsonPath("$.data.address").value("示例市健康路 20 号"))
                .andReturn().getResponse().getContentAsString();

        String hospitalId = objectMapper.readTree(created).at("/data/id").asText();
        mockMvc.perform(get("/api/v1/hospitals").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + hospitalId + "')].name")
                        .value("示例市第二人民医院"));
    }

    @Test
    void adminCanCreateDepartmentAndDoctorThatImmediatelyAppearInCatalog() throws Exception {
        String hospitalBody = mockMvc.perform(post("/api/v1/admin/hospitals")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"资源管理医院\",\"address\":\"管理路 1 号\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String hospitalId = objectMapper.readTree(hospitalBody).at("/data/id").asText();

        String departmentBody = mockMvc.perform(post("/api/v1/admin/hospitals/{id}/departments", hospitalId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" 心血管内科 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hospitalId").value(hospitalId))
                .andExpect(jsonPath("$.data.name").value("心血管内科"))
                .andReturn().getResponse().getContentAsString();
        String departmentId = objectMapper.readTree(departmentBody).at("/data/id").asText();

        String doctorBody = mockMvc.perform(post("/api/v1/admin/departments/{id}/doctors", departmentId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " 王医生 ",
                                  "title": " 副主任医师 ",
                                  "introduction": " 擅长心血管常见疾病诊治。 "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.departmentId").value(departmentId))
                .andExpect(jsonPath("$.data.doctorNo").value(org.hamcrest.Matchers.matchesPattern("DOC\\d{6,}")))
                .andExpect(jsonPath("$.data.name").value("王医生"))
                .andExpect(jsonPath("$.data.title").value("副主任医师"))
                .andExpect(jsonPath("$.data.introduction").value("擅长心血管常见疾病诊治。"))
                .andReturn().getResponse().getContentAsString();
        JsonNode doctor = objectMapper.readTree(doctorBody).path("data");

        mockMvc.perform(get("/api/v1/hospitals/{id}/departments", hospitalId)
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(departmentId));
        mockMvc.perform(get("/api/v1/departments/{id}/doctors", departmentId)
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(doctor.path("id").asText()))
                .andExpect(jsonPath("$.data[0].doctorNo").value(doctor.path("doctorNo").asText()));
    }

    @Test
    void adminCreatedResourceChainCanBeQueriedAndBookedByUser() throws Exception {
        String hospitalId = createHospital("全链路医院", "全链路 1 号");
        String departmentId = createDepartment(hospitalId, "全链路科室");
        JsonNode doctor = createDoctor(departmentId, "全链路医生", "主任医师", "全链路测试");
        LocalDate appointmentDate = LocalDate.now(appointmentClock).plusDays(2);

        mockMvc.perform(get("/api/v1/hospitals").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + hospitalId + "')]").isNotEmpty());
        mockMvc.perform(get("/api/v1/hospitals/{id}/departments", hospitalId)
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + departmentId + "')]").isNotEmpty());
        mockMvc.perform(get("/api/v1/departments/{id}/doctors", departmentId)
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + doctor.path("id").asText() + "')]").isNotEmpty());
        mockMvc.perform(get("/api/v1/doctors/{id}/schedules", doctor.path("id").asText())
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].sessionType").value("MORNING_FIRST"));

        Long userId = createUser();
        Long patientId = createPatient(userId);
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setDoctorId(doctor.path("id").asLong());
        request.setAppointmentDate(appointmentDate);
        request.setSessionType("MORNING_FIRST");
        request.setPatientId(patientId);
        CreateAppointmentResponse appointment = (CreateAppointmentResponse) bookingCoordinator.create(
                userId, UUID.randomUUID().toString(), request);
        org.assertj.core.api.Assertions.assertThat(appointment.getAppointmentOrderId()).isNotNull();
    }

    @Test
    void departmentNameIsUniqueWithinHospitalButCanRepeatAcrossHospitals() throws Exception {
        String firstHospitalId = createHospital("第一管理医院", "管理路 1 号");
        String secondHospitalId = createHospital("第二管理医院", "管理路 2 号");

        createDepartment(firstHospitalId, "心血管内科");
        mockMvc.perform(post("/api/v1/admin/hospitals/{id}/departments", firstHospitalId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" 心血管内科 \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4401));

        createDepartment(secondHospitalId, "心血管内科");
        String otherDepartmentId = createDepartment(firstHospitalId, "待改名科室");
        mockMvc.perform(patch("/api/v1/admin/departments/{id}", otherDepartmentId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"心血管内科\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4401));
    }

    @Test
    void adminCanUpdateProfilesWithoutChangingDoctorNumberOrDepartment() throws Exception {
        String hospitalId = createHospital("档案医院", "旧地址");
        String departmentId = createDepartment(hospitalId, "旧科室");
        JsonNode doctor = createDoctor(departmentId, "原医生", "医师", "原简介");

        mockMvc.perform(patch("/api/v1/admin/hospitals/{id}", hospitalId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" 新医院名 \",\"address\":\" 新地址 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新医院名"))
                .andExpect(jsonPath("$.data.address").value("新地址"));
        mockMvc.perform(patch("/api/v1/admin/departments/{id}", departmentId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" 新科室名 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新科室名"));
        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", doctor.path("id").asText())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "doctorNo": "DOC999999",
                                  "departmentId": "1",
                                  "name": " 新医生名 ",
                                  "title": " 主任医师 ",
                                  "introduction": " 新简介 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorNo").value(doctor.path("doctorNo").asText()))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId))
                .andExpect(jsonPath("$.data.name").value("新医生名"))
                .andExpect(jsonPath("$.data.title").value("主任医师"))
                .andExpect(jsonPath("$.data.introduction").value("新简介"));
    }

    @Test
    void referencedResourcesCannotBeDeletedButEmptyHierarchyCanBeRemovedBottomUp() throws Exception {
        String hospitalId = createHospital("受限删除医院", "删除路 1 号");
        String departmentId = createDepartment(hospitalId, "受限删除科室");
        JsonNode doctor = createDoctor(departmentId, "受限删除医生", "医师", "测试");

        expectResourceInUse(delete("/api/v1/admin/hospitals/{id}", hospitalId));
        expectResourceInUse(delete("/api/v1/admin/departments/{id}", departmentId));

        CreateScheduleRequest schedule = new CreateScheduleRequest();
        schedule.setDoctorId(doctor.path("id").asLong());
        schedule.setAppointmentDate(LocalDate.now(appointmentClock).plusDays(10));
        schedule.setSessionType("MORNING");
        schedule.setTotalCapacity(1);
        schedule.setRegistrationFee(new BigDecimal("50.00"));
        schedule.setFeeCurrency("CNY");
        scheduleManagementService.create(1L, schedule);
        expectResourceInUse(delete("/api/v1/admin/doctors/{id}", doctor.path("id").asText()));

        String emptyHospitalId = createHospital("空医院", "删除路 2 号");
        String emptyDepartmentId = createDepartment(emptyHospitalId, "空科室");
        JsonNode emptyDoctor = createDoctor(emptyDepartmentId, "无排班医生", "医师", "测试");
        expectDeleted(delete("/api/v1/admin/doctors/{id}", emptyDoctor.path("id").asText()));
        expectDeleted(delete("/api/v1/admin/departments/{id}", emptyDepartmentId));
        expectDeleted(delete("/api/v1/admin/hospitals/{id}", emptyHospitalId));

        mockMvc.perform(get("/api/v1/hospitals").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + emptyHospitalId + "')]").isEmpty());
    }

    @Test
    void ordinaryUserCannotCallCatalogManagementEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/admin/hospitals")
                        .with(user("ordinary").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权医院\",\"address\":\"越权地址\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(patch("/api/v1/admin/hospitals/1")
                        .with(user("ordinary").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(delete("/api/v1/admin/hospitals/1")
                        .with(user("ordinary").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(patch("/api/v1/admin/departments/1")
                        .with(user("ordinary").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(delete("/api/v1/admin/departments/1")
                        .with(user("ordinary").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(patch("/api/v1/admin/doctors/1")
                        .with(user("ordinary").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(delete("/api/v1/admin/doctors/1")
                        .with(user("ordinary").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(post("/api/v1/admin/hospitals/1/departments")
                        .with(user("ordinary").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权科室\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(post("/api/v1/admin/departments/1/doctors")
                        .with(user("ordinary").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权医生\",\"title\":\"医师\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    void missingParentsReturnNotFoundAndManualScheduleEndpointsAreGone() throws Exception {
        mockMvc.perform(post("/api/v1/admin/hospitals/999999/departments")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在父级\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(post("/api/v1/admin/departments/999999/doctors")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在父级\",\"title\":\"医师\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));

        String date = LocalDate.now(appointmentClock).plusDays(10).toString();
        mockMvc.perform(post("/api/v1/admin/schedules")
                        .with(authentication(new TestingAuthenticationToken(1L, null, "ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "doctorId": "1",
                                  "appointmentDate": "%s",
                                  "sessionType": "AFTERNOON",
                                  "totalCapacity": 2,
                                  "registrationFee": 50.00,
                                  "feeCurrency": "CNY"
                                }
                                """.formatted(date)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/schedules")
                        .with(authentication(new TestingAuthenticationToken(1L, null, "ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "doctorId": "999999",
                                  "appointmentDate": "%s",
                                  "sessionType": "MORNING",
                                  "totalCapacity": 2,
                                  "registrationFee": 50.00,
                                  "feeCurrency": "CNY"
                                }
                                """.formatted(date)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void blankValuesAndEmptyPatchesAreRejected() throws Exception {
        String hospitalId = createHospital("校验医院", "校验路 1 号");
        String departmentId = createDepartment(hospitalId, "校验科室");
        JsonNode doctor = createDoctor(departmentId, "校验医生", "医师", "测试");

        mockMvc.perform(patch("/api/v1/admin/hospitals/{id}", hospitalId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        mockMvc.perform(patch("/api/v1/admin/departments/{id}", departmentId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", doctor.path("id").asText())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", doctor.path("id").asText())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"introduction\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        mockMvc.perform(post("/api/v1/admin/departments/{id}/doctors", departmentId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"空白简介医生\",\"title\":\"医师\",\"introduction\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void profileUpdatesDoNotChangeExistingAppointmentVoucher() throws Exception {
        String hospitalId = createHospital("快照旧医院", "快照旧地址");
        String departmentId = createDepartment(hospitalId, "快照旧科室");
        JsonNode doctor = createDoctor(departmentId, "快照旧医生", "旧职称", "旧简介");
        Long doctorId = doctor.path("id").asLong();
        LocalDate appointmentDate = LocalDate.now(appointmentClock).plusDays(2);
        Long morningScheduleId = createSchedule(doctorId, appointmentDate, "MORNING_FIRST");
        Long afternoonScheduleId = createSchedule(doctorId, appointmentDate, "AFTERNOON_FIRST");
        Long userId = createUser();
        Long patientId = createPatient(userId);

        CreateAppointmentResponse first = createAppointment(userId, patientId, morningScheduleId);

        mockMvc.perform(patch("/api/v1/admin/hospitals/{id}", hospitalId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"快照新医院\",\"address\":\"快照新地址\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/departments/{id}", departmentId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"快照新科室\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", doctorId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"快照新医生\",\"title\":\"新职称\"}"))
                .andExpect(status().isOk());

        AppointmentResponse oldVoucher = appointmentService.getOwned(userId, first.getAppointmentOrderId());
        org.assertj.core.api.Assertions.assertThat(oldVoucher.getDoctor().getDoctorNo())
                .isEqualTo(doctor.path("doctorNo").asText());
        org.assertj.core.api.Assertions.assertThat(oldVoucher.getDoctor().getName()).isEqualTo("快照旧医生");
        org.assertj.core.api.Assertions.assertThat(oldVoucher.getDoctor().getTitle()).isEqualTo("旧职称");
        org.assertj.core.api.Assertions.assertThat(oldVoucher.getDoctor().getDepartment().getName())
                .isEqualTo("快照旧科室");
        org.assertj.core.api.Assertions.assertThat(oldVoucher.getDoctor().getHospital().getName())
                .isEqualTo("快照旧医院");
        org.assertj.core.api.Assertions.assertThat(oldVoucher.getDoctor().getHospital().getAddress())
                .isEqualTo("快照旧地址");

        CreateAppointmentResponse second = createAppointment(userId, patientId, afternoonScheduleId);
        AppointmentResponse newVoucher = appointmentService.getOwned(userId, second.getAppointmentOrderId());
        org.assertj.core.api.Assertions.assertThat(newVoucher.getDoctor().getName()).isEqualTo("快照新医生");
        org.assertj.core.api.Assertions.assertThat(newVoucher.getDoctor().getTitle()).isEqualTo("新职称");
        org.assertj.core.api.Assertions.assertThat(newVoucher.getDoctor().getDepartment().getName())
                .isEqualTo("快照新科室");
        org.assertj.core.api.Assertions.assertThat(newVoucher.getDoctor().getHospital().getName())
                .isEqualTo("快照新医院");
        org.assertj.core.api.Assertions.assertThat(newVoucher.getDoctor().getHospital().getAddress())
                .isEqualTo("快照新地址");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateDepartmentCreationAllowsOnlyOneSuccess() throws Exception {
        String departmentName = "并发科室-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = new TestExecutor(2)) {
            Future<Object> first = executor.submit(() -> createDepartmentConcurrently(departmentName, ready, start));
            Future<Object> second = executor.submit(() -> createDepartmentConcurrently(departmentName, ready, start));
            org.assertj.core.api.Assertions.assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(firstResult, secondResult))
                    .filteredOn(AdminDepartmentResponse.class::isInstance)
                    .hasSize(1);
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(firstResult, secondResult))
                    .filteredOn(ErrorCode.DEPARTMENT_NAME_EXISTS::equals)
                    .hasSize(1);

            AdminDepartmentResponse created = (AdminDepartmentResponse) java.util.List.of(firstResult, secondResult)
                    .stream().filter(AdminDepartmentResponse.class::isInstance).findFirst().orElseThrow();
            expectDeleted(delete("/api/v1/admin/departments/{id}", created.getId()));
        } finally {
            // 保留接口删除断言；失败时也清理本例随机名称的已提交数据。
            fixtureJdbc.update("DELETE FROM departments WHERE hospital_id=1 AND name=?", departmentName);
        }
    }

    private String createHospital(String name, String address) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/hospitals")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", name, "address", address))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    private String createDepartment(String hospitalId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/hospitals/{id}/departments", hospitalId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    private JsonNode createDoctor(String departmentId, String name, String title, String introduction)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/departments/{id}/doctors", departmentId)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", name, "title", title, "introduction", introduction))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private Long createSchedule(Long doctorId, LocalDate date, String sessionType) {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setDoctorId(doctorId);
        request.setAppointmentDate(date);
        request.setSessionType(sessionType);
        request.setTotalCapacity(2);
        request.setRegistrationFee(new BigDecimal("50.00"));
        request.setFeeCurrency("CNY");
        return scheduleManagementService.create(1L, request).getId();
    }

    private Long createUser() {
        User user = new User();
        user.setEmail("catalog-snapshot-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("unused");
        userMapper.insert(user);
        return user.getId();
    }

    private Long createPatient(Long userId) {
        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName("目录快照就诊人");
        patient.setDocumentType("RESIDENT_ID_CARD");
        patient.setDocumentNo("110105199001010010");
        patient.setContactPhone("13800138000");
        patient.setContactEmail("snapshot@example.com");
        patient.setEnabled(true);
        patientMapper.insert(patient);
        return patient.getId();
    }

    private CreateAppointmentResponse createAppointment(Long userId, Long patientId, Long scheduleId) {
        com.qsx.appoint.catalog.entity.Schedule schedule = scheduleMapper.selectById(scheduleId);
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setPatientId(patientId);
        request.setDoctorId(schedule.getDoctorId());
        request.setAppointmentDate(schedule.getAppointmentDate());
        request.setSessionType(schedule.getSessionType());
        AppointmentCreationResult result = bookingCoordinator.create(userId, UUID.randomUUID().toString(), request);
        return (CreateAppointmentResponse) result;
    }

    private void expectResourceInUse(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request.with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4402));
    }

    private void expectDeleted(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request.with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private Object createDepartmentConcurrently(String name, CountDownLatch ready, CountDownLatch start) {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName(name);
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("并发开始超时");
            return catalogManagementService.createDepartment(1L, request);
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
