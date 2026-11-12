package com.qsx.appoint.common.messaging;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 两条业务路径共享的传输契约：保留原文/ID、确认顺序和边界计数。 */
class RabbitConsumerFailureHandlerTest {
    final ConfirmedMessagePublisher publisher = mock(ConfirmedMessagePublisher.class);
    final Channel channel = mock(Channel.class);
    final RabbitConsumerFailureHandler handler = new RabbitConsumerFailureHandler(publisher);
    final RabbitRetryRoute route = new RabbitRetryRoute("retry","route","attempt");

    @org.junit.jupiter.api.Test
    void retryAckFailureEscapesWithoutRequeueOrSecondPublication() throws IOException {
        doThrow(new IOException("lost ack")).when(channel).basicAck(7,false);
        assertThatThrownBy(() -> handler.handle(new Message(new byte[0],new MessageProperties()),
                channel,7,"id",new IllegalStateException(),route)).isInstanceOf(IOException.class);
        verify(publisher,times(1)).publish(anyString(),anyString(),any(),any());
        verify(channel,never()).basicNack(anyLong(),anyBoolean(),anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(ints={0,1})
    void forwardsOriginalWithNextCountBeforeAcknowledging(int previous) throws Exception {
        MessageProperties properties=new MessageProperties();
        properties.setMessageId("original");
        properties.setHeader("attempt",(long)previous);
        Message original=new Message(new byte[]{1,2,3},properties);
        handler.handle(original,channel,7,"id",new IllegalStateException(),route);
        var order=inOrder(publisher,channel);
        order.verify(publisher).publish(eq("retry"),eq("route"),argThat(retry -> {
            assertThat(retry.getBody()).isEqualTo(original.getBody());
            assertThat(retry.getMessageProperties().getMessageId()).isEqualTo("original");
            assertThat(retry.getMessageProperties().<Integer>getHeader("attempt")).isEqualTo(previous+1);
            assertThat(retry.getMessageProperties().getExpiration()).isEqualTo("1000");
            return true;
        }),any());
        order.verify(channel).basicAck(7,false);
        assertThat(properties.<Long>getHeader("attempt")).isEqualTo((long)previous);
    }
    @ParameterizedTest
    @ValueSource(strings={"-1","2147483648","2147483647","2","text","1.5"})
    void rejectsExhaustedOrMalformedHeadersWithoutOverflow(String value) throws IOException {
        MessageProperties properties=new MessageProperties();
        Object raw;
        try { raw=Long.parseLong(value); } catch(NumberFormatException ignored) { raw=value; }
        properties.setHeader("attempt",raw);
        handler.handle(new Message(new byte[0],properties),channel,7,"id",new IllegalStateException(),route);
        verify(channel).basicReject(7,false);
        verifyNoInteractions(publisher);
    }
}
