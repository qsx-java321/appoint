package com.qsx.appoint.payment.application;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.mapper.*;
import com.qsx.appoint.payment.port.GatewayPaymentStatus;
import com.qsx.appoint.payment.port.VerifiedPaymentNotification;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentNotificationBoundaryTest {
    private final PaymentNotificationMapper notifications = mock(PaymentNotificationMapper.class);
    private final PaymentAttemptMapper attempts = mock(PaymentAttemptMapper.class);
    private final PaymentOrderMapper payments = mock(PaymentOrderMapper.class);
    private final PaymentOutbox outbox = mock(PaymentOutbox.class);
    private final PaymentRefundOperations refunds = mock(PaymentRefundOperations.class);
    private final PaymentNotificationService service = new PaymentNotificationServiceImpl(
            notifications, attempts, payments, outbox, refunds, Clock.systemUTC());
    private final VerifiedPaymentNotification notification = new VerifiedPaymentNotification(
            "notice", "provider-order", "transaction", GatewayPaymentStatus.SUCCEEDED,
            new BigDecimal("10.00"), "CNY", LocalDateTime.of(2026, 11, 9, 8, 0));

    @Test
    void unknownProviderOrderIsAuditedWithoutMoneyWrites() {
        assertThat(service.handle(PaymentChannel.MOCK, notification, "hash").processingResult())
                .isEqualTo("REJECTED_UNKNOWN_ORDER");
        verify(notifications).insert(argThat((com.qsx.appoint.payment.entity.PaymentNotification audit) -> "REJECTED_UNKNOWN_ORDER".equals(audit.getProcessingResult())));
        verifyNoInteractions(payments, outbox, refunds);
    }

    @Test
    void missingPaymentAssociationIsInternalErrorBeforeStateDecision() {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(1L);
        attempt.setPaymentOrderId(2L);
        when(attempts.selectByProviderOrderNo("MOCK", "provider-order")).thenReturn(attempt);
        assertThatThrownBy(() -> service.handle(PaymentChannel.MOCK, notification, "hash"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        verifyNoInteractions(outbox, refunds);
        verify(notifications, never()).insert(any(com.qsx.appoint.payment.entity.PaymentNotification.class));
    }
}
