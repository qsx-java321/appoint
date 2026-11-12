package com.qsx.appoint.common.messaging;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 直接验证真实处理器的传输矩阵；两个Listener另行验证业务接口及各自重试路由的接线。 */
class RabbitConsumerFailureHandlerTest {
    private final ConfirmedMessagePublisher publisher = mock(ConfirmedMessagePublisher.class);
    private final Channel channel = mock(Channel.class);
    private final RabbitConsumerFailureHandler handler = new RabbitConsumerFailureHandler(publisher);
    private final RabbitRetryRoute route = new RabbitRetryRoute("retry", "route", "attempt");

    @ParameterizedTest
    @MethodSource("retryableHeaders")
    void forwardsOriginalWithNextCountBeforeAcknowledging(Object previous) throws IOException {
        Message original = message(previous);
        int expected = previous == null ? 1 : ((Number) previous).intValue() + 1;

        handler.handle(original, channel, 7, "id", new IllegalStateException(), route);

        var order = inOrder(publisher, channel);
        order.verify(publisher).publish(eq("retry"), eq("route"), argThat(retry -> {
            assertThat(retry).isNotSameAs(original);
            assertThat(retry.getBody()).isEqualTo(original.getBody());
            assertThat(retry.getMessageProperties().getMessageId()).isEqualTo("original");
            assertThat(retry.getMessageProperties().<Integer>getHeader("attempt")).isEqualTo(expected);
            assertThat(retry.getMessageProperties().getExpiration()).isEqualTo("1000");
            return true;
        }), eq(Duration.ofSeconds(5)));
        order.verify(channel).basicAck(7, false);
        verifyNoMoreInteractions(publisher, channel);
        assertThat(original.getMessageProperties().<Object>getHeader("attempt")).isEqualTo(previous);
        assertThat(original.getMessageProperties().getExpiration()).isNull();
    }

    static Stream<Object> retryableHeaders() { return Stream.of(null, 0, 0L, 1, 1L); }

    @ParameterizedTest
    @MethodSource("rejectedHeaders")
    void rejectsExhaustedOrMalformedHeadersWithoutOverflow(Object value) throws IOException {
        handler.handle(message(value), channel, 7, "id", new IllegalStateException(), route);

        verify(channel).basicReject(7, false);
        verifyNoMoreInteractions(channel);
        verifyNoInteractions(publisher);
    }

    static Stream<Object> rejectedHeaders() {
        return Stream.of(-1L, 2147483648L, 2147483647L, 2L, "text", "1.5",
                -1, 2, Integer.MAX_VALUE, 1.5, true, "1");
    }

    @Test
    void uncertainPublicationRequeuesOriginalWithoutAcknowledging() throws IOException {
        doThrow(new IllegalStateException("confirmation uncertain"))
                .when(publisher).publish(anyString(), anyString(), any(), any());

        handler.handle(message(null), channel, 7, "id", new IllegalStateException(), route);

        var order = inOrder(publisher, channel);
        order.verify(publisher).publish(anyString(), anyString(), any(), any());
        order.verify(channel).basicNack(7, false, true);
        verifyNoMoreInteractions(publisher, channel);
    }

    @Test
    void nackFailurePropagatesWithoutAcknowledgingOrRepublishing() throws IOException {
        doThrow(new IllegalStateException("confirmation uncertain"))
                .when(publisher).publish(anyString(), anyString(), any(), any());
        IOException lost = new IOException("lost nack");
        doThrow(lost).when(channel).basicNack(7, false, true);

        assertThatThrownBy(() -> handler.handle(message(null), channel, 7, "id",
                new IllegalStateException(), route)).isSameAs(lost);

        verify(publisher).publish(anyString(), anyString(), any(), any());
        verify(channel).basicNack(7, false, true);
        verifyNoMoreInteractions(publisher, channel);
    }

    @Test
    void retryAckFailureEscapesWithoutRequeueOrSecondPublication() throws IOException {
        IOException lost = new IOException("lost ack");
        doThrow(lost).when(channel).basicAck(7, false);

        assertThatThrownBy(() -> handler.handle(message(null), channel, 7, "id",
                new IllegalStateException(), route)).isSameAs(lost);

        var order = inOrder(publisher, channel);
        order.verify(publisher).publish(anyString(), anyString(), any(), any());
        order.verify(channel).basicAck(7, false);
        verifyNoMoreInteractions(publisher, channel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectFailurePropagatesForValidationAndExhaustion(boolean invalidInput) throws IOException {
        IOException lost = new IOException("lost reject");
        doThrow(lost).when(channel).basicReject(7, false);
        RuntimeException failure = invalidInput
                ? new BusinessException(ErrorCode.VALIDATION_ERROR) : new IllegalStateException();

        assertThatThrownBy(() -> handler.handle(message(2), channel, 7, "id", failure, route)).isSameAs(lost);

        verify(channel).basicReject(7, false);
        verifyNoMoreInteractions(channel);
        verifyNoInteractions(publisher);
    }

    private Message message(Object previous) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("original");
        if (previous != null) properties.setHeader("attempt", previous);
        return new Message(new byte[]{1, 2, 3}, properties);
    }
}
