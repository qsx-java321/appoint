package com.qsx.appoint.payment.messaging;

import com.qsx.appoint.common.messaging.ConfirmedMessagePublisher;
import com.qsx.appoint.payment.application.RefundRequestedEvent;
import com.qsx.appoint.payment.application.RefundWorker;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 直接构造真实Listener和失败处理器；验证本入口接线，完整传输矩阵由公共处理器测试负责。 */
class RefundRequestedListenerTest {
    private final RefundRequestedEventCodec codec = mock(RefundRequestedEventCodec.class);
    private final RefundWorker worker = mock(RefundWorker.class);
    private final ConfirmedMessagePublisher publisher = mock(ConfirmedMessagePublisher.class);
    private final Channel channel = mock(Channel.class);
    private final RefundRequestedListener listener = new RefundRequestedListener(codec, worker, new com.qsx.appoint.common.messaging.RabbitConsumerFailureHandler(publisher));

    @Test
    void successfulBusinessDoesNotInspectMalformedRetryHeader() throws IOException {
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
        Message original = message();
        original.getMessageProperties().setHeader("refund-attempt", "bad");
        when(codec.read(any())).thenReturn(event);
        when(worker.process(event)).thenReturn(com.qsx.appoint.payment.application.RefundExecutionResult.SUCCEEDED);
        listener.consume(original,channel);
        var order = inOrder(codec, worker, channel);
        order.verify(codec).read(original.getBody());
        order.verify(worker).process(event);
        order.verify(channel).basicAck(7,false);
        verifyNoInteractions(publisher);
    }

    @Test
    void invalidBusinessInputIsRejectedWithoutRetry() throws IOException {
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(worker.process(event)).thenThrow(new com.qsx.appoint.common.BusinessException(
                com.qsx.appoint.common.ErrorCode.VALIDATION_ERROR));
        listener.consume(message(),channel);
        verify(channel).basicReject(7,false);
        verifyNoInteractions(publisher);
    }

    private Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7);
        return new Message(new byte[]{1}, properties);
    }

    @Test
    void processingFailurePublishesBeforeAcknowledging() throws IOException {
        Message message = message();
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
        when(event.eventId()).thenReturn("event-1");
        when(codec.read(any())).thenReturn(event);
        when(worker.process(event)).thenThrow(new IllegalStateException("failed"));

        listener.consume(message, channel);

        var order = inOrder(publisher, channel);
        order.verify(publisher).publish(eq(RabbitEventTopology.RETRY_EXCHANGE),
                eq(RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY), argThat(retry ->
                        Integer.valueOf(1).equals(retry.getMessageProperties().getHeader("refund-attempt"))), eq(Duration.ofSeconds(5)));
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void permanentInvalidMessageDoesNotRetry() throws IOException {
        when(codec.read(any())).thenThrow(new com.qsx.appoint.common.BusinessException(
                com.qsx.appoint.common.ErrorCode.VALIDATION_ERROR));
        listener.consume(message(), channel);
        verify(channel).basicReject(7, false);
        verifyNoInteractions(worker, publisher);
    }

    @Test
    void ackFailureAfterSuccessfulProcessingPropagatesForRedelivery() throws IOException {
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(worker.process(event)).thenReturn(com.qsx.appoint.payment.application.RefundExecutionResult.SUCCEEDED);
        doThrow(new IOException("ACK lost")).when(channel).basicAck(7, false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.consume(message(), channel))
                .isInstanceOf(IOException.class);
        var order = inOrder(worker, channel);
        order.verify(worker).process(event);
        order.verify(channel).basicAck(7, false);
        verifyNoInteractions(publisher);
    }

}
