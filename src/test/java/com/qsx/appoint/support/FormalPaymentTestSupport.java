package com.qsx.appoint.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.appointment.dto.AppointmentResponse;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.application.PaymentSucceededProcessor;
import com.qsx.appoint.coordination.messaging.PaymentSucceededEventCodec;
import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.application.PaymentAttemptService;
import com.qsx.appoint.payment.application.PaymentLifecycle;
import com.qsx.appoint.payment.application.PaymentNotificationIngress;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import com.qsx.appoint.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/** MockMvc夹具通过支付尝试、验签和Outbox驱动业务消费者，不连接RabbitMQ。 */
@TestComponent
@RequiredArgsConstructor
public class FormalPaymentTestSupport {
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final javax.sql.DataSource dataSource;
    private final PaymentLifecycle payments;
    private final PaymentAttemptService attempts;
    private final PaymentAttemptMapper attemptMapper;
    private final MockPaymentGateway gateway;
    private final PaymentNotificationIngress ingress;
    private final PaymentOutboxEventMapper outbox;
    private final PaymentSucceededEventCodec codec;
    private final PaymentSucceededProcessor processor;
    private final AppointmentService appointments;
    private final JwtTokenProvider tokens;
    private final Clock appointmentClock;

    public AppointmentResponse payWithToken(String token, Long paymentId) {
        return pay(Long.valueOf(tokens.parseClaims(token).getSubject()),paymentId);
    }
    public AppointmentResponse payWithToken(String token, String paymentId) {
        return payWithToken(token,Long.valueOf(paymentId));
    }
    public AppointmentResponse pay(Long userId, Long paymentId) {
        var payment = payments.find(paymentId);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!payment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        String providerOrderNo;
        if (payment.status() == PaymentOrderStatus.PAYING || payment.status() == PaymentOrderStatus.PAID) {
            var existing = attemptMapper.selectList(new LambdaQueryWrapper<PaymentAttempt>()
                    .eq(PaymentAttempt::getPaymentOrderId,paymentId)
                    .in(PaymentAttempt::getStatus,"PROCESSING","SUCCEEDED")
                    .orderByDesc(PaymentAttempt::getId));
            if (existing.isEmpty()) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
            providerOrderNo = existing.getFirst().getProviderOrderNo();
        } else {
            providerOrderNo = attempts.startAttempt(userId,paymentId,PaymentChannel.MOCK).providerOrderNo();
        }
        var raw = gateway.complete(providerOrderNo,"formal-test-"+UUID.randomUUID(),LocalDateTime.now(appointmentClock));
        ingress.handle(PaymentChannel.MOCK,raw);
        var events = outbox.selectList(new LambdaQueryWrapper<PaymentOutboxEvent>()
                .eq(PaymentOutboxEvent::getAggregateId,paymentId).eq(PaymentOutboxEvent::getEventType,"PaymentSucceeded"));
        if (events.size() != 1) throw new AssertionError("每单必须只有一个真实PaymentSucceeded事件");
        processor.process(codec.read(storedPayload(events.getFirst()).getBytes(StandardCharsets.UTF_8)));
        return appointments.getOwned(userId,payment.appointmentOrderId());
    }
    private String storedPayload(PaymentOutboxEvent event) {
        // H2把String参数写入JSON列时额外编码为JSON字符串；仅测试适配这一驱动差异。
        try (var connection=dataSource.getConnection()) {
            if ("H2".equals(connection.getMetaData().getDatabaseProductName())) {
                var stored=objectMapper.readTree(event.getPayload());
                if (stored.isTextual()) return stored.textValue();
            }
            return event.getPayload();
        } catch (java.sql.SQLException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError("读取测试Outbox失败",exception);
        }
    }

}
