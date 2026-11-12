package com.qsx.appoint.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.messaging.ConfirmedMessagePublisher;
import com.qsx.appoint.payment.application.*;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.rabbitmq.client.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 三个逻辑目标、无ID/空队列区别，以及转发确认先于源ACK的适配契约。 */
class DeadLetterReplayContractTest {
    final RabbitTemplate template=mock(RabbitTemplate.class);
    final Channel channel=mock(Channel.class);
    final ConfirmedMessagePublisher publisher=mock(ConfirmedMessagePublisher.class);
    final RabbitDeadLetterReplayService service=new RabbitDeadLetterReplayService(template,new ObjectMapper(),publisher);

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"RefundFailed","Unknown"})
    void resultReplaySelectsFailureRouteOrPreservesUnsupportedMessage(String type) throws Exception {
        when(template.execute(any())).thenAnswer(call -> ((ChannelCallback<?>)call.getArgument(0)).doInRabbit(channel));
        when(channel.basicGet(anyString(),eq(false))).thenReturn(new GetResponse(new Envelope(7,false,"",""),
                new AMQP.BasicProperties(),("{\"eventType\":\""+type+"\"}").getBytes(StandardCharsets.UTF_8),0));
        if (type.equals("Unknown")) {
            assertThatThrownBy(() -> service.replayOne(ReplayTarget.REFUND_RESULT)).isInstanceOf(IllegalArgumentException.class);
            verify(channel).basicNack(7,false,true);
            verifyNoInteractions(publisher);
        } else {
            assertThat(service.replayOne(ReplayTarget.REFUND_RESULT).status()).isEqualTo(ReplayResult.Status.REPLAYED);
            verify(publisher).publish(eq(RabbitEventTopology.DOMAIN_EXCHANGE),eq(RabbitEventTopology.REFUND_FAILED_ROUTING_KEY),any(),any());
            verify(channel).basicAck(7,false);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void confirmationIoFailurePropagatesWithoutFabricatingSuccess(boolean ackFailure) throws Exception {
        when(template.execute(any())).thenAnswer(call -> ((ChannelCallback<?>)call.getArgument(0)).doInRabbit(channel));
        when(channel.basicGet(anyString(),eq(false))).thenReturn(new GetResponse(new Envelope(7,false,"",""),
                new AMQP.BasicProperties(),"{}".getBytes(StandardCharsets.UTF_8),0));
        if (ackFailure) doThrow(new java.io.IOException("ack lost")).when(channel).basicAck(7,false);
        else {
            doThrow(new IllegalStateException("send uncertain")).when(publisher).publish(anyString(),anyString(),any(),any());
            doThrow(new java.io.IOException("nack lost")).when(channel).basicNack(7,false,true);
        }
        assertThatThrownBy(() -> service.replayOne(ReplayTarget.PAYMENT_SUCCEEDED)).isInstanceOf(java.io.IOException.class);
        verify(channel).basicNack(7,false,true);
        verify(publisher,times(1)).publish(anyString(),anyString(),any(),any());
    }

    @ParameterizedTest
    @EnumSource(ReplayTarget.class)
    void logicalTargetsKeepOriginalBodyAndDistinguishEmptyFromMissingId(ReplayTarget target) throws Exception {
        when(template.execute(any())).thenAnswer(call -> ((ChannelCallback<?>)call.getArgument(0)).doInRabbit(channel));
        byte[] body="{\"eventType\":\"RefundSucceeded\"}".getBytes(StandardCharsets.UTF_8);
        var properties=new AMQP.BasicProperties.Builder().headers(Map.of("refund-attempt",2,"payment-succeeded-attempt",2)).build();
        var delivery=new GetResponse(new Envelope(7,false,"",""),properties,body,0);
        when(channel.basicGet(anyString(),eq(false))).thenReturn(delivery,null);
        ReplayResult result=service.replayOne(target);
        assertThat(result.status()).isEqualTo(ReplayResult.Status.REPLAYED);
        assertThat(result.eventId()).isNull();
        String queue=switch(target) {
            case PAYMENT_SUCCEEDED -> RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE;
            case REFUND_REQUESTED -> RabbitEventTopology.REFUND_WORKER_QUEUE;
            case REFUND_RESULT -> RabbitEventTopology.REFUND_RESULT_QUEUE;
        };
        String route=switch(target) {
            case PAYMENT_SUCCEEDED -> RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY;
            case REFUND_REQUESTED -> RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY;
            case REFUND_RESULT -> RabbitEventTopology.REFUND_SUCCEEDED_ROUTING_KEY;
        };
        verify(channel).basicGet(queue+".dlq",false);
        var order=inOrder(publisher,channel);
        order.verify(publisher).publish(eq(RabbitEventTopology.DOMAIN_EXCHANGE),eq(route),argThat(message -> {
            assertThat(message.getBody()).isEqualTo(body);
            assertThat(message.getMessageProperties().getHeaders()).doesNotContainKeys("refund-attempt","payment-succeeded-attempt");
            return true;
        }),any());
        order.verify(channel).basicAck(7,false);
        assertThat(service.replayOne(target).status()).isEqualTo(ReplayResult.Status.EMPTY);
    }

    @ParameterizedTest
    @EnumSource(ReplayTarget.class)
    void uncertainPublicationKeepsOriginalDelivery(ReplayTarget target) throws Exception {
        when(template.execute(any())).thenAnswer(call -> ((ChannelCallback<?>)call.getArgument(0)).doInRabbit(channel));
        var delivery=new GetResponse(new Envelope(7,false,"",""),new AMQP.BasicProperties(),
                "{\"eventType\":\"RefundSucceeded\"}".getBytes(StandardCharsets.UTF_8),0);
        when(channel.basicGet(anyString(),eq(false))).thenReturn(delivery);
        doThrow(new IllegalStateException("uncertain")).when(publisher).publish(anyString(),anyString(),any(),any());
        assertThatThrownBy(() -> service.replayOne(target)).isInstanceOf(RuntimeException.class);
        verify(channel).basicNack(7,false,true);
        verify(channel,never()).basicAck(anyLong(),anyBoolean());
    }
}
