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

class PaymentSucceededListenerTest {
    private final PaymentSucceededEventCodec codec = mock(PaymentSucceededEventCodec.class);
    private final PaymentSucceededProcessor processor = mock(PaymentSucceededProcessor.class);
    private final ConfirmedMessagePublisher publisher = mock(ConfirmedMessagePublisher.class);
    private final Channel channel = mock(Channel.class);
    private final PaymentSucceededListener listener = new PaymentSucceededListener(publisher, codec, processor);

    private Message message(int failures) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7);
        if (failures > 0) properties.setHeader("payment-succeeded-attempt", failures);
        return new Message(new byte[]{1}, properties);
    }

    @Test
    void processingFailurePublishesBeforeAcknowledging() throws IOException {
        Message message = message(0);
        PaymentSucceededEvent event = mock(PaymentSucceededEvent.class);
        when(event.eventId()).thenReturn("event-1");
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenThrow(new IllegalStateException("failed"));

        listener.consume(message, channel);

        var order = inOrder(publisher, channel);
        order.verify(publisher).publish(eq(RabbitEventTopology.RETRY_EXCHANGE),
                eq(RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY), argThat(retry ->
                        Integer.valueOf(1).equals(retry.getMessageProperties().getHeader("payment-succeeded-attempt"))
                                && "1000".equals(retry.getMessageProperties().getExpiration())), eq(Duration.ofSeconds(5)));
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void failedRetryPublicationRequeuesOriginal() throws IOException {
        Message message = message(0);
        PaymentSucceededEvent event = mock(PaymentSucceededEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenThrow(new IllegalStateException("failed"));
        doThrow(new IllegalStateException("unroutable")).when(publisher).publish(anyString(), anyString(), any(), any());

        listener.consume(message, channel);

        verify(channel).basicNack(7, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void thirdFailureIsDeadLettered() throws IOException {
        Message message = message(2);
        PaymentSucceededEvent event = mock(PaymentSucceededEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenThrow(new IllegalStateException("failed"));

        listener.consume(message, channel);

        verify(channel).basicReject(7, false);
        verifyNoInteractions(publisher);
    }
    @Test
    void permanentInvalidMessageDoesNotRetry() throws IOException {
        when(codec.read(any())).thenThrow(new com.qsx.appoint.common.BusinessException(
                com.qsx.appoint.common.ErrorCode.VALIDATION_ERROR));
        listener.consume(message(0), channel);
        verify(channel).basicReject(7, false);
        verifyNoInteractions(processor, publisher);
    }

    @Test
    void ackFailureAfterCommittedProcessingPropagatesForRedelivery() throws IOException {
        PaymentSucceededEvent event = mock(PaymentSucceededEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenReturn(com.qsx.appoint.coordination.application.PaymentSucceededResult.CONFIRMED);
        doThrow(new IOException("ACK lost")).when(channel).basicAck(7, false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.consume(message(0), channel))
                .isInstanceOf(IOException.class);
        var order = inOrder(processor, channel);
        order.verify(processor).process(event);
        order.verify(channel).basicAck(7, false);
        verifyNoInteractions(publisher);
    }

}
