package com.qsx.appoint.coordination.messaging;

import com.qsx.appoint.common.messaging.ConfirmedMessagePublisher;
import com.qsx.appoint.coordination.application.PaymentSucceededEvent;
import com.qsx.appoint.coordination.application.PaymentSucceededProcessor;
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
class PaymentSucceededListenerTest {
    private final PaymentSucceededEventCodec codec = mock(PaymentSucceededEventCodec.class);
    private final PaymentSucceededProcessor processor = mock(PaymentSucceededProcessor.class);
    private final ConfirmedMessagePublisher publisher = mock(ConfirmedMessagePublisher.class);
    private final Channel channel = mock(Channel.class);
    private final PaymentSucceededListener listener = new PaymentSucceededListener(new com.qsx.appoint.common.messaging.RabbitConsumerFailureHandler(publisher), codec, processor);

    @Test
    void successfulBusinessDoesNotInspectMalformedRetryHeader() throws IOException {
        PaymentSucceededEvent event = mock(PaymentSucceededEvent.class);
        Message original = message();
        original.getMessageProperties().setHeader("payment-succeeded-attempt", "bad");
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenReturn(com.qsx.appoint.coordination.application.PaymentSucceededResult.CONFIRMED);
        listener.consume(original,channel);
        var order = inOrder(codec, processor, channel);
        order.verify(codec).read(original.getBody());
        order.verify(processor).process(event);
        order.verify(channel).basicAck(7,false);
        verifyNoInteractions(publisher);
    }

    @Test
    void invalidBusinessInputIsRejectedWithoutRetry() throws IOException {
        PaymentSucceededEvent event = mock(PaymentSucceededEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenThrow(new com.qsx.appoint.common.BusinessException(
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
        PaymentSucceededEvent event = mock(PaymentSucceededEvent.class);
        when(event.eventId()).thenReturn("event-1");
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenThrow(new IllegalStateException("failed"));

        listener.consume(message, channel);

        var order = inOrder(publisher, channel);
        order.verify(publisher).publish(eq(RabbitEventTopology.RETRY_EXCHANGE),
                eq(RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY), argThat(retry ->
                        Integer.valueOf(1).equals(retry.getMessageProperties().getHeader("payment-succeeded-attempt"))), eq(Duration.ofSeconds(5)));
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void permanentInvalidMessageDoesNotRetry() throws IOException {
        when(codec.read(any())).thenThrow(new com.qsx.appoint.common.BusinessException(
                com.qsx.appoint.common.ErrorCode.VALIDATION_ERROR));
        listener.consume(message(), channel);
        verify(channel).basicReject(7, false);
        verifyNoInteractions(processor, publisher);
    }

    @Test
    void ackFailureAfterSuccessfulProcessingPropagatesForRedelivery() throws IOException {
        PaymentSucceededEvent event = mock(PaymentSucceededEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenReturn(com.qsx.appoint.coordination.application.PaymentSucceededResult.CONFIRMED);
        doThrow(new IOException("ACK lost")).when(channel).basicAck(7, false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.consume(message(), channel))
                .isInstanceOf(IOException.class);
        var order = inOrder(processor, channel);
        order.verify(processor).process(event);
        order.verify(channel).basicAck(7, false);
        verifyNoInteractions(publisher);
    }

}
