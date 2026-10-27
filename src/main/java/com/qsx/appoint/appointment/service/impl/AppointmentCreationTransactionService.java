package com.qsx.appoint.appointment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.config.AppointmentProperties;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.entity.AppointmentCreateRequest;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 将幂等登记、快照、占号以及两个订单作为一个原子事务提交。 */
@Service
@RequiredArgsConstructor
public class AppointmentCreationTransactionService {
    private final AppointmentCreateRequestMapper createRequestMapper;
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

        Patient patient = patientMapper.selectByIdForUpdate(request.getPatientId());
        if (patient == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!patient.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!Boolean.TRUE.equals(patient.getEnabled())) throw new BusinessException(ErrorCode.PATIENT_DISABLED);

        Schedule schedule = scheduleMapper.selectByIdForUpdate(request.getScheduleId());
        if (schedule == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        LocalDateTime appointmentStart = LocalDateTime.of(schedule.getAppointmentDate(), schedule.getStartTime());
        if (!appointmentStart.isAfter(now)) throw new BusinessException(ErrorCode.SCHEDULE_STARTED);

        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        Department department = doctor == null ? null : departmentMapper.selectById(doctor.getDepartmentId());
        Hospital hospital = department == null ? null : hospitalMapper.selectById(department.getHospitalId());
        if (doctor == null || department == null || hospital == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (scheduleMapper.holdIfAvailable(schedule.getId()) != 1) {
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
}
