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

/**
 * 在同库创建事务中完成预约侧暂占：按需生成场次、锁定就诊人、占号、保存快照与有效占用。
 * <p>统一设置仅决定首次生成场次的容量/费用；已生成场次以自身快照为准。
 * 创建时间用于费用凭证及支付截止，最终占号日期必须重新读取Clock，不能沿用请求开始日期。
 * @see AppointmentHoldService
 */
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

    /**
     * {@inheritDoc}
     * <p>主方法保留业务顺序。支付单由外层随后创建，异常必须向外传播以回滚场次与暂占。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AppointmentHold createHold(CreateAppointmentHoldCommand command) {
        LocalDateTime now = command.createdAt();
        AppointmentSession session = AppointmentSession.require(command.sessionType());
        validateBookingDate(command.appointmentDate());
        Doctor doctor = doctorMapper.selectById(command.doctorId());
        if (doctor == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Schedule schedule = lockOrCreateSchedule(command, session);

        // 先场次后就诊人：保持创建锁序，并避免占号与资料更新交错生成不完整凭证。
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

        // 唯一占用冲突会回滚前面的库存和预约；不能捕获后继续完成支付建单。
        claimActiveAppointment(schedule, appointment, patient, now);

        return new AppointmentHold(appointment.getId(), appointment.getAppointmentNo(), command.userId(),
                appointment.getStatus(), appointment.getFeeAmount(), appointment.getFeeCurrency(),
                appointment.getPaymentExpiresAt(), now);
    }

    /**
     * 锁统一设置，按医生/日期/场次唯一键插入或重读场次，再锁定并校验可预约性。
     * <p>并发首次下单依靠唯一键收敛；设置变更不覆盖已有场次快照。
     * @param command 目标预约身份及日期
     * @param session 已解析的新四场次类型
     * @return 已加锁且通过可预约性检查的场次
     */
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

    /**
     * 在实际占号写入点重新检查日期并执行available→held条件更新。
     * <p>设置、场次或就诊人锁可能等待跨零点；前面的日期检查不能代替本次最终日期验收。
     * @param schedule 当前事务已锁定的场次
     * @param appointmentDate 请求的就诊日期，必须仍处于最终日期的D+1至D+7
     */
    private void holdOne(Schedule schedule, LocalDate appointmentDate) {
        validateBookingDate(appointmentDate);
        LocalDate finalAcceptanceDate = LocalDate.now(appointmentClock);
        validateBookingDate(appointmentDate, finalAcceptanceDate);
        if (scheduleMapper.holdOneFutureSession(schedule.getId(), finalAcceptanceDate) != 1) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
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
     * <p>费用来自场次快照，截止为外层创建时间加支付超时；历史订单不跟随资料或设置变化。
     * @return 尚未插入的PENDING_PAYMENT预约Entity，仅在本模块内部使用
     */
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

    /**
     * 校验场次存在、新场次身份、正数CNY费用、开放状态及可用库存。
     * <p>此处是业务快速拒绝，最终库存仍由条件UPDATE保护，不能用内存判断代替数据库约束。
     */
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

    /**
     * 读取此时服务端日期后检查预约窗口；可能在同一创建过程中重复调用以处理跨零点。
     * @param appointmentDate 请求的就诊日期
     */
    private void validateBookingDate(LocalDate appointmentDate) {
        LocalDate today = LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS).toLocalDate();
        validateBookingDate(appointmentDate, today);
    }

    /**
     * 校验就诊日期严格落在给定日期的D+1至D+7，包含两个端点。
     * @param appointmentDate 请求的就诊日期，null不合法
     * @param today 本次资格判断的服务端日期
     * @throws com.qsx.appoint.common.BusinessException 日期越界时为SCHEDULE_CUTOFF_REACHED
     */
    private void validateBookingDate(LocalDate appointmentDate, LocalDate today) {
        if (appointmentDate == null || appointmentDate.isBefore(today.plusDays(1))
                || appointmentDate.isAfter(today.plusDays(7))) {
            throw new BusinessException(ErrorCode.SCHEDULE_CUTOFF_REACHED);
        }
    }
}

