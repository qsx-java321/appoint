package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.entity.AppointmentCreateRequest;
import com.qsx.appoint.appointment.mapper.AppointmentCreateRequestMapper;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class AppointmentCreationRequestServiceImpl implements AppointmentCreationRequestService {
    private final AppointmentCreateRequestMapper mapper;
    private final AppointmentOrderMapper appointmentOrderMapper;

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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long requestId, Long appointmentOrderId, String responseJson) {
        if (mapper.complete(requestId, appointmentOrderId, responseJson) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public AppointmentCreationReplay find(Long userId, String idempotencyKey) {
        AppointmentCreateRequest request = mapper.selectByUserAndKey(userId, idempotencyKey);
        if (request == null) return null;
        AppointmentOrder appointment = request.getAppointmentOrderId() == null
                ? null : appointmentOrderMapper.selectById(request.getAppointmentOrderId());
        return new AppointmentCreationReplay(request.getRequestHash(), request.getAppointmentOrderId(),
                appointment == null ? null : appointment.getAppointmentNo(),
                request.getResponseJson() != null && appointment != null);
    }
}

