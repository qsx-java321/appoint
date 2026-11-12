package com.qsx.appoint.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.coordination.messaging.PaymentSucceededEventCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 固定改造前的 wire 样本，特别保护生产 Long 文本、null 和消费者数字/文本双兼容。 */
@SpringBootTest
@ActiveProfiles("test")
class MessagingBaselineContractTest {
    @Autowired ObjectMapper mapper;
    @Autowired PaymentSucceededEventCodec codec;

    @Test
    void productionMapperKeepsTextLongAndNullWhileHistoricalFixturesRemainReadable() throws Exception {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("id", 51L);
        values.put("failureMessage", null);
        var serialized = mapper.readTree(mapper.writeValueAsBytes(values));
        assertThat(serialized.get("id").isTextual()).isTrue();
        assertThat(serialized.has("failureMessage")).isTrue();
        assertThat(serialized.get("failureMessage").isNull()).isTrue();
        for (String type : new String[]{"PaymentSucceeded", "RefundRequested", "RefundSucceeded", "RefundFailed"}) {
            for (String suffix : new String[]{"", "-numeric"}) {
                try (var input = getClass().getResourceAsStream("/messaging/v1/" + type + suffix + ".json")) {
                    assertThat(input).isNotNull();
                    byte[] body = input.readAllBytes();
                    var event = mapper.readTree(body);
                    assertThat(event.path("eventType").asText()).isEqualTo(type);
                    assertThat(event.path("aggregateId").asLong()).isEqualTo(51L);
                    if (type.equals("PaymentSucceeded")) assertThat(codec.read(body).paymentOrderId()).isEqualTo(51L);
                }
            }
        }
    }
}
