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

class RefundRequestedListenerTest {
    private final RefundRequestedEventCodec codec = mock(RefundRequestedEventCodec.class);
    private final RefundWorker processor = mock(RefundWorker.class);
    private final ConfirmedMessagePublisher publisher = mock(ConfirmedMessagePublisher.class);
    private final Channel channel = mock(Channel.class);
    private final RefundRequestedListener listener = new RefundRequestedListener(codec, processor, new com.qsx.appoint.common.messaging.RabbitConsumerFailureHandler(publisher));

    @Test
    void successfulBusinessDoesNotInspectMalformedRetryHeader() throws IOException {
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
        Message original = message(0);
        original.getMessageProperties().setHeader("refund-attempt", "bad");
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenReturn(com.qsx.appoint.payment.application.RefundExecutionResult.SUCCEEDED);
        listener.consume(original,channel);
        verify(channel).basicAck(7,false);
        verifyNoInteractions(publisher);
    }

    @Test
    void invalidBusinessInputIsRejectedWithoutRetry() throws IOException {
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenThrow(new com.qsx.appoint.common.BusinessException(
                com.qsx.appoint.common.ErrorCode.VALIDATION_ERROR));
        listener.consume(message(0),channel);
        verify(channel).basicReject(7,false);
        verifyNoInteractions(publisher);
    }

    private Message message(int failures) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7);
        if (failures > 0) properties.setHeader("refund-attempt", failures);
        return new Message(new byte[]{1}, properties);
    }

    @Test
    void processingFailurePublishesBeforeAcknowledging() throws IOException {
        Message message = message(0);
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
        when(event.eventId()).thenReturn("event-1");
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenThrow(new IllegalStateException("failed"));

        listener.consume(message, channel);

        var order = inOrder(publisher, channel);
        order.verify(publisher).publish(eq(RabbitEventTopology.RETRY_EXCHANGE),
                eq(RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY), argThat(retry ->
                        Integer.valueOf(1).equals(retry.getMessageProperties().getHeader("refund-attempt"))
                                && "1000".equals(retry.getMessageProperties().getExpiration())), eq(Duration.ofSeconds(5)));
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void failedRetryPublicationRequeuesOriginal() throws IOException {
        Message message = message(0);
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
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
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
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
        RefundRequestedEvent event = mock(RefundRequestedEvent.class);
        when(codec.read(any())).thenReturn(event);
        when(processor.process(event)).thenReturn(com.qsx.appoint.payment.application.RefundExecutionResult.SUCCEEDED);
        doThrow(new IOException("ACK lost")).when(channel).basicAck(7, false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.consume(message(0), channel))
                .isInstanceOf(IOException.class);
        var order = inOrder(processor, channel);
        order.verify(processor).process(event);
        order.verify(channel).basicAck(7, false);
        verifyNoInteractions(publisher);
    }

}
