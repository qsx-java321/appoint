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
import com.qsx.appoint.catalog.model.AppointmentSession;
import com.qsx.appoint.catalog.model.BookingTimeRules;
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
import java.time.temporal.ChronoUnit;

/**
 * 在同库创建事务中完成预约侧暂占：按需生成场次、锁定就诊人、占号、保存快照与有效占用。
 * <p>统一设置仅决定首次生成场次的容量/费用；已生成场次以自身快照为准。
 * 条件更新取得场次锁，就诊人也锁定后再采样准入时间并复验日期与截止。
 * @see AppointmentHoldService
 */
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
    private final Clock appointmentClock;

    /**
     * {@inheritDoc}
     * <p>主方法保留业务顺序。支付单由外层随后创建，异常必须向外传播以回滚场次与暂占。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AppointmentHold createHold(CreateAppointmentHoldCommand command) {
        AppointmentSession session = AppointmentSession.require(command.sessionType());
        LocalDateTime checkedAt = now();
        validateBookingTime(command.appointmentDate(), session.startTime(), checkedAt);
        Doctor doctor = doctorMapper.selectById(command.doctorId());
        if (doctor == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Schedule schedule = holdOne(command, session, checkedAt);

        // 先场次后就诊人：保持创建锁序，并避免占号与资料更新交错生成不完整凭证。
        Patient patient = requireOwnedPatient(command.patientId(), command.userId());

        Department department = departmentMapper.selectById(doctor.getDepartmentId());
        Hospital hospital = department == null ? null : hospitalMapper.selectById(department.getHospitalId());
        if (department == null || hospital == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        LocalDateTime acceptedAt = now();
        validateBookingTime(schedule.getAppointmentDate(), schedule.getStartTime(), acceptedAt);

        AppointmentOrder appointment = createAppointmentSnapshot(
                command, schedule, patient, doctor, department, hospital, acceptedAt);
        appointmentOrderMapper.insert(appointment);

        // 唯一占用冲突会回滚前面的库存和预约；不能捕获后继续完成支付建单。
        claimActiveAppointment(schedule, appointment, patient, acceptedAt);

        return new AppointmentHold(appointment.getId(), appointment.getAppointmentNo(), command.userId(),
                appointment.getStatus(), appointment.getFeeAmount(), appointment.getFeeCurrency(),
                appointment.getPaymentExpiresAt(), acceptedAt);
    }

    /**
     * 仅首次预约从统一设置生成场次。建场后读取当前行，包含其他事务刚提交的场次。
     */
    private Long findOrCreateScheduleId(CreateAppointmentHoldCommand command, AppointmentSession session) {
        Schedule schedule = scheduleMapper.selectByDoctorDateSession(
                command.doctorId(), command.appointmentDate(), session.name());
        if (schedule == null) {
            scheduleMapper.insertGeneratedFromSettings(command.doctorId(), command.appointmentDate(),
                    session.startTime(), session.endTime(), session.name());
            schedule = scheduleMapper.selectByDoctorDateSessionForUpdate(
                    command.doctorId(), command.appointmentDate(), session.name());
            if (schedule == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return schedule.getId();
    }

    /**
     * 条件更新取得场次行锁并暂占，随后用本事务已更新的场次生成凭证。
     * <p>SQL使用入口校验时间。等待锁期间跨过截止时，后续校验会回滚暂占。
     */
    private Schedule holdOne(CreateAppointmentHoldCommand command, AppointmentSession session,
                             LocalDateTime checkedAt) {
        Long scheduleId = findOrCreateScheduleId(command, session);
        var dates = BookingTimeRules.selectableDates(checkedAt);
        LocalDateTime earliestStart = checkedAt.plus(BookingTimeRules.MINIMUM_BOOKING_ADVANCE);
        if (scheduleMapper.holdOneBookableSession(scheduleId, command.doctorId(), command.appointmentDate(),
                session.name(), dates.getFirst(), dates.getLast(),
                earliestStart.toLocalDate(), earliestStart.toLocalTime()) != 1) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        if (schedule.getRegistrationFee() == null || schedule.getRegistrationFee().signum() <= 0
                || !"CNY".equals(schedule.getFeeCurrency())) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
        return schedule;
    }

    /**
     * 登记有效预约占用，由唯一约束防止同就诊人或同规范化证件重复持有本场预约。
     * <p>跨账号同证件也受约束；统一返回重复有效预约错误，不泄露其他账号订单。
     * @param schedule 已暂占的场次
     * @param appointment 已插入且获得ID的预约
     * @param patient 加锁读取的就诊人资料
     * @param now 创建时间，与预约凭证保持一致
     */
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

    /**
     * 在场次锁之后锁就诊人，依次检查存在、归属和启用状态。
     * <p>与就诊人更新串行化，保证凭证来自一份完整资料；不允许跳过归属检查。
     * @return 当前事务锁定的就诊人；资格失败抛对应业务错误
     */
    private Patient requireOwnedPatient(Long patientId, Long userId) {
        Patient patient = patientMapper.selectByIdForUpdate(patientId);
        if (patient == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!patient.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!Boolean.TRUE.equals(patient.getEnabled())) throw new BusinessException(ErrorCode.PATIENT_DISABLED);
        return patient;
    }

    /**
     * 从已验证的目录、就诊人与场次复制不可变预约凭证。
     * <p>费用来自场次快照，截止取准入时间加支付时限与场次开始的较早者；历史订单不重算。
     * @return 尚未插入的PENDING_PAYMENT预约Entity，仅在本模块内部使用
     */
    private AppointmentOrder createAppointmentSnapshot(CreateAppointmentHoldCommand command, Schedule schedule,
                                                        Patient patient, Doctor doctor, Department department,
                                                        Hospital hospital, LocalDateTime now) {
        AppointmentOrder appointment = new AppointmentOrder();
        appointment.setAppointmentNo(command.appointmentNo());
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
        appointment.setPaymentExpiresAt(BookingTimeRules.paymentExpiresAt(now,
                schedule.getAppointmentDate().atTime(schedule.getStartTime()), appointmentProperties.paymentTimeout()));
        appointment.setFeeAmount(schedule.getRegistrationFee().setScale(2, RoundingMode.UNNECESSARY));
        appointment.setFeeCurrency(schedule.getFeeCurrency());
        return appointment;
    }

    private void validateBookingTime(java.time.LocalDate appointmentDate, java.time.LocalTime startTime,
                                      LocalDateTime acceptanceTime) {
        if (!BookingTimeRules.canCreate(appointmentDate, startTime, acceptanceTime)) {
            throw new BusinessException(ErrorCode.SCHEDULE_CUTOFF_REACHED);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

