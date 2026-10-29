package com.qsx.appoint.appointment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.config.AppointmentProperties;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.entity.AppointmentCreateRequest;
import com.qsx.appoint.appointment.entity.AppointmentActiveClaim;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentActiveClaimMapper;
import com.qsx.appoint.appointment.mapper.AppointmentCreateRequestMapper;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.catalog.entity.Department;
import com.qsx.appoint.catalog.entity.Doctor;
import com.qsx.appoint.catalog.entity.Hospital;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.DepartmentMapper;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.catalog.mapper.HospitalMapper;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 将幂等登记、快照、占号以及两个订单作为一个原子事务提交。 */
@Service
@RequiredArgsConstructor
public class AppointmentCreationTransactionService {
    private final AppointmentCreateRequestMapper createRequestMapper;
    private final AppointmentActiveClaimMapper activeClaimMapper;
    private final PatientMapper patientMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final HospitalMapper hospitalMapper;
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final AppointmentProperties appointmentProperties;
    private final Clock appointmentClock;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateAppointmentResponse create(Long userId, String idempotencyKey, String requestHash,
                                            CreateAppointmentRequest request) {
        LocalDateTime now = now();
        AppointmentCreateRequest createRequest = new AppointmentCreateRequest();
        createRequest.setUserId(userId);
        createRequest.setIdempotencyKey(idempotencyKey);
        createRequest.setRequestHash(requestHash);
        createRequest.setCreatedAt(now);
        createRequestMapper.insert(createRequest);

        Schedule schedule = scheduleMapper.selectByIdForUpdate(request.getScheduleId());
        validateBookable(schedule, now);

        Patient patient = patientMapper.selectByIdForUpdate(request.getPatientId());
        if (patient == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!patient.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!Boolean.TRUE.equals(patient.getEnabled())) throw new BusinessException(ErrorCode.PATIENT_DISABLED);

        LocalDateTime appointmentStart = LocalDateTime.of(schedule.getAppointmentDate(), schedule.getStartTime());

        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        Department department = doctor == null ? null : departmentMapper.selectById(doctor.getDepartmentId());
        Hospital hospital = department == null ? null : hospitalMapper.selectById(department.getHospitalId());
        if (doctor == null || department == null || hospital == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (scheduleMapper.holdOne(schedule.getId()) != 1) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }

        AppointmentOrder appointment = new AppointmentOrder();
        appointment.setAppointmentNo("AP" + UUID.randomUUID().toString().replace("-", ""));
        appointment.setUserId(userId);
        appointment.setScheduleId(schedule.getId());
        appointment.setPatientId(patient.getId());
        appointment.setPatientName(patient.getName());
        appointment.setPatientDocumentType(patient.getDocumentType());
        appointment.setPatientDocumentNo(patient.getDocumentNo());
        appointment.setPatientContactPhone(patient.getContactPhone());
        appointment.setPatientContactEmail(patient.getContactEmail());
        appointment.setDoctorId(doctor.getId());
        appointment.setDoctorNo(doctor.getDoctorNo());
        appointment.setDoctorName(doctor.getName());
        appointment.setDoctorTitle(doctor.getTitle());
        appointment.setDepartmentId(department.getId());
        appointment.setDepartmentName(department.getName());
        appointment.setHospitalId(hospital.getId());
        appointment.setHospitalName(hospital.getName());
        appointment.setHospitalAddress(hospital.getAddress());
        appointment.setAppointmentDate(schedule.getAppointmentDate());
        appointment.setStartTime(schedule.getStartTime());
        appointment.setEndTime(schedule.getEndTime());
        appointment.setStatus("PENDING_PAYMENT");
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);
        LocalDateTime timeoutAt = now.plus(appointmentProperties.paymentTimeout());
        appointment.setPaymentExpiresAt(timeoutAt.isBefore(appointmentStart) ? timeoutAt : appointmentStart);
        appointmentOrderMapper.insert(appointment);

        AppointmentActiveClaim claim = new AppointmentActiveClaim();
        claim.setScheduleId(schedule.getId());
        claim.setAppointmentOrderId(appointment.getId());
        claim.setPatientId(patient.getId());
        claim.setDocumentType(patient.getDocumentType());
        claim.setDocumentNo(patient.getDocumentNo());
        claim.setCreatedAt(now);
        try {
            activeClaimMapper.insert(claim);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_APPOINTMENT);
        }

        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo("PAY" + UUID.randomUUID().toString().replace("-", ""));
        payment.setAppointmentOrderId(appointment.getId());
        payment.setUserId(userId);
        payment.setStatus("UNPAID");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentOrderMapper.insert(payment);

        CreateAppointmentResponse response = new CreateAppointmentResponse(appointment.getId(), payment.getId(),
                appointment.getStatus(), payment.getStatus(), appointment.getPaymentExpiresAt(), now);
        if (createRequestMapper.complete(createRequest.getId(), appointment.getId(), serialize(response)) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return response;
    }

    private String serialize(CreateAppointmentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private void validateBookable(Schedule schedule, LocalDateTime now) {
        if (schedule == null || schedule.getSessionType() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("CLOSED".equals(schedule.getStatus())) throw new BusinessException(ErrorCode.SCHEDULE_CLOSED);
        if (!"OPEN".equals(schedule.getStatus())) throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        LocalDateTime start = LocalDateTime.of(schedule.getAppointmentDate(), schedule.getStartTime());
        LocalDateTime release = LocalDateTime.of(schedule.getAppointmentDate().minusDays(7), java.time.LocalTime.of(8, 0));
        if (now.isBefore(release)) throw new BusinessException(ErrorCode.SCHEDULE_NOT_OPEN);
        if (!now.isBefore(start.minusMinutes(30))) throw new BusinessException(ErrorCode.SCHEDULE_CUTOFF_REACHED);
        if (schedule.getAvailableCapacity() == null || schedule.getAvailableCapacity() <= 0) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
    }
}
