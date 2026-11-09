package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.domain.AppointmentStatus;
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
import com.qsx.appoint.catalog.mapper.BookingSettingsMapper;
import com.qsx.appoint.catalog.entity.BookingSettings;
import com.qsx.appoint.catalog.model.AppointmentSession;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AppointmentHoldServiceImpl implements AppointmentHoldService {
    private final AppointmentActiveClaimMapper activeClaimMapper;
    private final PatientMapper patientMapper;
    private final ScheduleMapper scheduleMapper;
    private final BookingSettingsMapper bookingSettingsMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final HospitalMapper hospitalMapper;
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final AppointmentProperties appointmentProperties;
    private final Clock appointmentClock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AppointmentHold createHold(CreateAppointmentHoldCommand command) {
        LocalDateTime now = command.createdAt();
        AppointmentSession session = AppointmentSession.require(command.sessionType());
        validateBookingDate(command.appointmentDate());
        Doctor doctor = doctorMapper.selectById(command.doctorId());
        if (doctor == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Schedule schedule = lockOrCreateSchedule(command, session);

        Patient patient = requireOwnedPatient(command.patientId(), command.userId());

        Department department = departmentMapper.selectById(doctor.getDepartmentId());
        Hospital hospital = department == null ? null : hospitalMapper.selectById(department.getHospitalId());
        if (department == null || hospital == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        holdOne(schedule, command.appointmentDate());

        AppointmentOrder appointment = createAppointmentSnapshot(
                command, schedule, patient, doctor, department, hospital, now);
        appointmentOrderMapper.insert(appointment);

        claimActiveAppointment(schedule, appointment, patient, now);

        return new AppointmentHold(appointment.getId(), appointment.getAppointmentNo(), command.userId(),
                appointment.getStatus(), appointment.getFeeAmount(), appointment.getFeeCurrency(),
                appointment.getPaymentExpiresAt(), now);
    }

    private Schedule lockOrCreateSchedule(CreateAppointmentHoldCommand command, AppointmentSession session) {
        BookingSettings settings = bookingSettingsMapper.selectForUpdate();
        if (settings == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        scheduleMapper.insertGenerated(command.doctorId(), command.appointmentDate(),
                session.startTime(), session.endTime(), session.name(), settings.getCapacityPerSession(),
                settings.getRegistrationFee(), settings.getCurrency());
        Schedule schedule = scheduleMapper.selectByDoctorDateSessionForUpdate(
                command.doctorId(), command.appointmentDate(), session.name());
        validateBookingDate(command.appointmentDate());
        validateBookable(schedule);
        return schedule;
    }

    /** 等待设置、场次、就诊人锁后重新读日期；SQL条件校验仍使用最终日期。 */
    private void holdOne(Schedule schedule, LocalDate appointmentDate) {
        validateBookingDate(appointmentDate);
        LocalDate finalAcceptanceDate = LocalDate.now(appointmentClock);
        validateBookingDate(appointmentDate, finalAcceptanceDate);
        if (scheduleMapper.holdOneFutureSession(schedule.getId(), finalAcceptanceDate) != 1) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
    }

    private void claimActiveAppointment(Schedule schedule, AppointmentOrder appointment, Patient patient,
                                        LocalDateTime now) {
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
    }

    private Patient requireOwnedPatient(Long patientId, Long userId) {
        Patient patient = patientMapper.selectByIdForUpdate(patientId);
        if (patient == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!patient.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!Boolean.TRUE.equals(patient.getEnabled())) throw new BusinessException(ErrorCode.PATIENT_DISABLED);
        return patient;
    }

    private AppointmentOrder createAppointmentSnapshot(CreateAppointmentHoldCommand command, Schedule schedule,
                                                        Patient patient, Doctor doctor, Department department,
                                                        Hospital hospital, LocalDateTime now) {
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
        appointment.setStatus(AppointmentStatus.PENDING_PAYMENT.name());
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);
        appointment.setPaymentExpiresAt(now.plus(appointmentProperties.paymentTimeout()));
        appointment.setFeeAmount(schedule.getRegistrationFee().setScale(2, RoundingMode.UNNECESSARY));
        appointment.setFeeCurrency(schedule.getFeeCurrency());
        return appointment;
    }

    private void validateBookable(Schedule schedule) {
        if (schedule == null || schedule.getSessionType() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (schedule.getRegistrationFee() == null || schedule.getRegistrationFee().signum() <= 0
                || !"CNY".equals(schedule.getFeeCurrency())) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
        if (!"OPEN".equals(schedule.getStatus())) throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        if (schedule.getAvailableCapacity() == null || schedule.getAvailableCapacity() <= 0) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
    }

    private void validateBookingDate(LocalDate appointmentDate) {
        LocalDate today = LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS).toLocalDate();
        validateBookingDate(appointmentDate, today);
    }

    private void validateBookingDate(LocalDate appointmentDate, LocalDate today) {
        if (appointmentDate == null || appointmentDate.isBefore(today.plusDays(1))
                || appointmentDate.isAfter(today.plusDays(7))) {
            throw new BusinessException(ErrorCode.SCHEDULE_CUTOFF_REACHED);
        }
    }
}

