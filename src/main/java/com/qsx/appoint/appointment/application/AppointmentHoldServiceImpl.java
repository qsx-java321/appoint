package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.config.AppointmentProperties;
import com.qsx.appoint.appointment.entity.AppointmentActiveClaim;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentActiveClaimMapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AppointmentHoldServiceImpl implements AppointmentHoldService {
    private final AppointmentActiveClaimMapper activeClaimMapper;
    private final PatientMapper patientMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final HospitalMapper hospitalMapper;
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final AppointmentProperties appointmentProperties;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AppointmentHold createHold(CreateAppointmentHoldCommand command) {
        LocalDateTime now = command.createdAt();
        Schedule schedule = scheduleMapper.selectByIdForUpdate(command.scheduleId());
        validateBookable(schedule, now);

        Patient patient = patientMapper.selectByIdForUpdate(command.patientId());
        if (patient == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!patient.getUserId().equals(command.userId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!Boolean.TRUE.equals(patient.getEnabled())) throw new BusinessException(ErrorCode.PATIENT_DISABLED);

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
        appointment.setUserId(command.userId());
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
        LocalDateTime appointmentStart = LocalDateTime.of(schedule.getAppointmentDate(), schedule.getStartTime());
        LocalDateTime timeoutAt = now.plus(appointmentProperties.paymentTimeout());
        appointment.setPaymentExpiresAt(timeoutAt.isBefore(appointmentStart) ? timeoutAt : appointmentStart);
        appointment.setFeeAmount(schedule.getRegistrationFee().setScale(2, RoundingMode.UNNECESSARY));
        appointment.setFeeCurrency(schedule.getFeeCurrency());
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

        return new AppointmentHold(appointment.getId(), appointment.getAppointmentNo(), command.userId(),
                appointment.getStatus(), appointment.getFeeAmount(), appointment.getFeeCurrency(),
                appointment.getPaymentExpiresAt(), now);
    }

    private void validateBookable(Schedule schedule, LocalDateTime now) {
        if (schedule == null || schedule.getSessionType() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (schedule.getRegistrationFee() == null || schedule.getRegistrationFee().signum() <= 0
                || !"CNY".equals(schedule.getFeeCurrency())) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
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

