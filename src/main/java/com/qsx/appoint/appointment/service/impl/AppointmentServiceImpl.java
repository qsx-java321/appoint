package com.qsx.appoint.appointment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 创建预约时先原子占用号源，再创建两个关联订单。
 * 任一写入失败都会回滚，使号源与订单不会出现不一致。
 */
@Service 
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {
    private final ScheduleMapper scheduleMapper;
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    @Transactional
    public CreateAppointmentResponse create(Long userId, CreateAppointmentRequest request) {
        Schedule schedule = scheduleMapper.selectById(request.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (scheduleMapper.holdIfAvailable(schedule.getId()) != 1) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }

        AppointmentOrder appointment = new AppointmentOrder();
        appointment.setAppointmentNo("AP" + UUID.randomUUID().toString().replace("-", ""));
        appointment.setUserId(userId);
        appointment.setScheduleId(schedule.getId());
        appointment.setStatus("PENDING_PAYMENT");
        appointmentOrderMapper.insert(appointment);

        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo("PAY" + UUID.randomUUID().toString().replace("-", ""));
        payment.setAppointmentOrderId(appointment.getId());
        payment.setUserId(userId);
        payment.setStatus("UNPAID");
        paymentOrderMapper.insert(payment);
        return new CreateAppointmentResponse(appointment.getId(), payment.getId(), appointment.getStatus(), payment.getStatus());
    }

    @Override
    public AppointmentResponse getOwned(Long userId, Long orderId) {
        AppointmentOrder order = appointmentOrderMapper.selectById(orderId);
        ensureOwner(userId, order);
        return toResponse(order);
    }

    @Override
    public List<AppointmentResponse> listMine(Long userId) {
        return appointmentOrderMapper.selectList(new LambdaQueryWrapper<AppointmentOrder>()
                        .eq(AppointmentOrder::getUserId, userId).orderByDesc(AppointmentOrder::getId))
                .stream().map(this::toResponse).toList();
    }

    private void ensureOwner(Long userId, AppointmentOrder order) {
        if (order == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!order.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private AppointmentResponse toResponse(AppointmentOrder order) {
        return new AppointmentResponse(order.getId(), order.getAppointmentNo(), order.getScheduleId(), order.getStatus(), order.getCreatedAt());
    }
}
