package com.qsx.appoint.appointment.service.impl;

import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.appointment.config.AppointmentProperties;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentQueryRecord;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
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
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 创建预约时先原子占用号源，再创建两个关联订单。
 * 任一写入失败都会回滚，使号源与订单不会出现不一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final HospitalMapper hospitalMapper;
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final AppointmentLifecycleService lifecycleService;
    private final AppointmentProperties appointmentProperties;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public CreateAppointmentResponse create(Long userId, CreateAppointmentRequest request) {
        Schedule schedule = scheduleMapper.selectByIdForUpdate(request.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        LocalDateTime now = now();
        LocalDateTime appointmentStart = LocalDateTime.of(schedule.getAppointmentDate(), schedule.getStartTime());
        if (!appointmentStart.isAfter(now)) {
            throw new BusinessException(ErrorCode.SCHEDULE_STARTED);
        }
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
        LocalDateTime timeoutAt = now.plus(appointmentProperties.paymentTimeout());
        appointment.setPaymentExpiresAt(timeoutAt.isBefore(appointmentStart) ? timeoutAt : appointmentStart);
        appointmentOrderMapper.insert(appointment);

        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo("PAY" + UUID.randomUUID().toString().replace("-", ""));
        payment.setAppointmentOrderId(appointment.getId());
        payment.setUserId(userId);
        payment.setStatus("UNPAID");
        payment.setCreatedAt(now);
        paymentOrderMapper.insert(payment);
        return new CreateAppointmentResponse(appointment.getId(), payment.getId(), appointment.getStatus(),
                payment.getStatus(), appointment.getPaymentExpiresAt(), now);
    }

    @Override
    public AppointmentResponse getOwned(Long userId, Long orderId) {
        AppointmentQueryRecord record = appointmentOrderMapper.selectQueryById(orderId);
        ensureOwner(userId, record);
        return toResponse(record, now());
    }

    @Override
    public List<AppointmentResponse> listMine(Long userId) {
        LocalDateTime serverTime = now();
        return appointmentOrderMapper.selectQueriesByUserId(userId).stream()
                .map(record -> toResponse(record, serverTime))
                .toList();
    }

    @Override
    public AppointmentResponse cancel(Long userId, Long orderId) {
        lifecycleService.cancelOwned(userId, orderId);
        return getOwned(userId, orderId);
    }

    private void ensureOwner(Long userId, AppointmentQueryRecord record) {
        if (record == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!record.getAppointmentUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private AppointmentResponse toResponse(AppointmentQueryRecord record, LocalDateTime serverTime) {
        ensurePaymentIntegrity(record);
        AppointmentDoctorResponse doctor = new AppointmentDoctorResponse(
                record.getDoctorId(), record.getDoctorNo(), record.getDoctorName(), record.getDoctorTitle(),
                new AppointmentDepartmentResponse(record.getDepartmentId(), record.getDepartmentName()),
                new AppointmentHospitalResponse(record.getHospitalId(), record.getHospitalName(), record.getHospitalAddress()));
        boolean paymentExpired = "EXPIRED".equals(record.getStatus())
                || ("PENDING_PAYMENT".equals(record.getStatus())
                && !serverTime.isBefore(record.getPaymentExpiresAt()));
        boolean pendingAndUnpaid = "PENDING_PAYMENT".equals(record.getStatus())
                && "UNPAID".equals(record.getPaymentStatus());
        return new AppointmentResponse(record.getId(), record.getAppointmentNo(), record.getScheduleId(),
                record.getAppointmentDate(), record.getStartTime(), record.getEndTime(), record.getStatus(),
                record.getCreatedAt(), record.getPaymentOrderId(), record.getPaymentStatus(),
                record.getPaymentExpiresAt(), record.getClosedAt(), serverTime, paymentExpired,
                pendingAndUnpaid && !paymentExpired, pendingAndUnpaid, doctor);
    }

    private void ensurePaymentIntegrity(AppointmentQueryRecord record) {
        if (record.getPaymentOrderId() == null) {
            log.error("预约查询发现缺失支付单，appointmentOrderId={}", record.getId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (!record.getAppointmentUserId().equals(record.getPaymentUserId())) {
            log.error("预约查询发现支付单归属不一致，appointmentOrderId={}, paymentOrderId={}",
                    record.getId(), record.getPaymentOrderId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}
