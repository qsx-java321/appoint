package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.entity.AppointmentCreateRequest;
import com.qsx.appoint.appointment.mapper.AppointmentCreateRequestMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 以创建请求表维护幂等事实，不在本类开启独立创建事务。
 * 登记、预约和回执跟随BookingCreationTransaction一起提交。
 */
@Service
@RequiredArgsConstructor
class AppointmentCreationRequestServiceImpl implements AppointmentCreationRequestService {
    private final AppointmentCreateRequestMapper mapper;

    /**
     * {@inheritDoc}
     * <p>数据库唯一键负责并发同键争用；冲突直接交给外层事务回滚。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long register(Long userId, String idempotencyKey, String requestHash, LocalDateTime createdAt) {
        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setUserId(userId);
        request.setIdempotencyKey(idempotencyKey);
        request.setRequestHash(requestHash);
        request.setCreatedAt(createdAt);
        mapper.insert(request);
        return request.getId();
    }

    /**
     * {@inheritDoc}
     * <p>保存回执也属于创建事务，写入失败不能留下已占名额但没有完成回执的订单。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long requestId, Long appointmentOrderId, String responseJson) {
        if (mapper.complete(requestId, appointmentOrderId, responseJson) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** {@inheritDoc} */
    @Override
    public AppointmentCreationReplay find(Long userId, String idempotencyKey) {
        AppointmentCreateRequest request = mapper.selectByUserAndKey(userId, idempotencyKey);
        if (request == null) return null;
        return new AppointmentCreationReplay(request.getRequestHash(), request.getResponseJson());
    }
}

