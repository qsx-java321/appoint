package com.qsx.appoint.common.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用RabbitTemplate/Channel替身模拟confirm、return和超时；真实Broker行为由可靠性测试负责。 */
class ConfirmedMessagePublisherTest {
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final ConfirmedMessagePublisher publisher = new ConfirmedMessagePublisher(template);
    private final Message message = new Message(new byte[]{1}, new MessageProperties());

    @ParameterizedTest
    @ValueSource(strings = {"nack", "return", "timeout", "connection"})
    void uncertainOrRejectedPublicationNeverReturnsSuccess(String failure) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (failure.equals("connection")) throw new IllegalStateException("connection lost");
            if (failure.equals("return")) correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", "exchange", "route"));
            if (!failure.equals("timeout")) correlation.getFuture().complete(new CorrelationData.Confirm(!failure.equals("nack"), "test"));
            return null;
        }).when(template).send(eq("exchange"), eq("route"), same(message), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher.publish("exchange", "route", message, Duration.ofMillis(10)))
                .isInstanceOf(IllegalStateException.class);
        verify(template).setMandatory(true);
    }

    @Test
    void confirmedAndRoutablePublicationSucceeds() {
        doAnswer(invocation -> {
            invocation.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq("exchange"), eq("route"), same(message), any(CorrelationData.class));
        assertThatCode(() -> publisher.publish("exchange", "route", message, Duration.ofSeconds(1)))
                .doesNotThrowAnyException();
    }
}
