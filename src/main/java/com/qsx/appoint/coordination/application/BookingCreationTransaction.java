package com.qsx.appoint.coordination.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.application.AppointmentCreationRequestService;
import com.qsx.appoint.appointment.application.AppointmentHold;
import com.qsx.appoint.appointment.application.AppointmentHoldService;
import com.qsx.appoint.appointment.application.CreateAppointmentHoldCommand;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.CreatePaymentOrderCommand;
import com.qsx.appoint.payment.application.PaymentOrderCreationService;
import com.qsx.appoint.payment.application.PaymentOrderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 一次首次创建的同库事务：幂等登记→预约暂占→支付建单→完成回执。
 * <p>单独作为Spring Bean供事务外协调入口调用，避免自调用绕过事务代理。
 * 预约和支付接口均以MANDATORY加入本事务，任何失败回滚新场次、库存、订单及登记。
 */
@Service
@RequiredArgsConstructor
class BookingCreationTransaction {
    private final AppointmentCreationRequestService creationRequestService;
    private final AppointmentHoldService holdService;
    private final PaymentOrderCreationService paymentOrderCreationService;
    private final Clock appointmentClock;
    private final ObjectMapper objectMapper;

    /**
     * 原子创建一张预约及其支付单，完成首次创建回执。
     * @param userId 已认证账号用户ID
     * @param idempotencyKey 外层已规范化的UUID键
     * @param requestHash 外层计算的预约内容摘要
     * @param request 经过HTTP结构校验的预约内容，业务资格仍由预约模块验证
     * @return 首次创建结果及本次服务端时间，不是重放回执
     * @throws org.springframework.dao.DuplicateKeyException 并发同键登记冲突，交给外层回滚后判断重放
     */
    @Transactional
    public CreateAppointmentResponse create(Long userId, String idempotencyKey, String requestHash,
                                             CreateAppointmentRequest request) {
        LocalDateTime requestRegisteredAt = LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
        // 先登记幂等意图，让同账号同键的并发请求在数据库唯一键上串行；后续失败一起回滚。
        Long requestId = creationRequestService.register(userId, idempotencyKey, requestHash, requestRegisteredAt);
        AppointmentHold hold = holdService.createHold(
                new CreateAppointmentHoldCommand(userId, request.getDoctorId(), request.getAppointmentDate(),
                        request.getSessionType(), request.getPatientId()));
        // 支付金额和截止只复制暂占结果，不再读取当前统一设置，避免一张预约出现两份费用。
        PaymentOrderResult payment = paymentOrderCreationService.createOrder(new CreatePaymentOrderCommand(
                hold.appointmentOrderId(), hold.userId(), hold.amount(), hold.currency(),
                hold.paymentExpiresAt(), hold.createdAt()));
        CreateAppointmentResponse response = new CreateAppointmentResponse(
                hold.appointmentOrderId(), payment.paymentOrderId(), hold.status(), payment.status(),
                payment.amount(), payment.currency(), hold.paymentExpiresAt(),
                LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS));
        // 预约与支付均已创建后才完成回执；序列化或完成登记失败仍回滚整笔事务。
        creationRequestService.complete(requestId, hold.appointmentOrderId(), serialize(response));
        return response;
    }

    /**
     * 序列化首次创建结果供幂等登记保存。
     * <p>序列化失败也要让创建事务回滚，不能留下没有完整回执的成功占号。
     * @return 首次结果JSON，重放接口不会直接拿它当最新状态
     */
    private String serialize(CreateAppointmentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
