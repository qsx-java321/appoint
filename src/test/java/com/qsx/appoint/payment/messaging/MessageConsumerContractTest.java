package com.qsx.appoint.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.coordination.messaging.PaymentSucceededEventCodec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** 对两种消费格式运行同一非法输入矩阵，合法样本来自改造前的固定资源。 */
class MessageConsumerContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final RefundRequestedEventCodec refund = new RefundRequestedEventCodec(mapper);
    private final PaymentSucceededEventCodec payment = new PaymentSucceededEventCodec(mapper);

    @ParameterizedTest
    @ValueSource(strings={"", "-numeric"})
    void historicalIntegerIdsAndAdditionalFieldsRemainCompatible(String suffix) throws Exception {
        for (String type : new String[]{"PaymentSucceeded","RefundRequested"}) {
            var node = fixture(type,suffix);
            node.put("futureField","ignored");
            assertThatCode(() -> read(type,node)).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"1.0","1.5","1e0","0","-1","9223372036854775808","true","[]","{}","\" \"","\"1.5\"","null"})
    void rejectsIdsWithoutTruncationOrImplicitDefaults(String value) throws Exception {
        for (String type : new String[]{"PaymentSucceeded","RefundRequested"}) {
            var node=fixture(type,"");
            ((ObjectNode)node.get("payload")).set("paymentOrderId",mapper.readTree(value));
            assertThatThrownBy(() -> read(type,node)).isInstanceOf(BusinessException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"type","version","fractionVersion","textVersion","aggregate","time","payload","missing"})
    void rejectsWrongEnvelope(String invalid) throws Exception {
        for (String type : new String[]{"PaymentSucceeded","RefundRequested"}) {
            var node=fixture(type,"");
            switch(invalid) {
                case "type" -> node.put("eventType","Other");
                case "version" -> node.put("eventVersion",2);
                case "fractionVersion" -> node.put("eventVersion",1.0);
                case "textVersion" -> node.put("eventVersion","1");
                case "aggregate" -> node.put("aggregateId","52");
                case "time" -> node.put("occurredAt","yesterday");
                case "payload" -> node.put("payload","invalid");
                default -> node.remove("eventId");
            }
            assertThatThrownBy(() -> read(type,node)).isInstanceOf(BusinessException.class);
        }
    }
    private ObjectNode fixture(String type,String suffix) throws Exception {
        try(var input=getClass().getResourceAsStream("/messaging/v1/"+type+suffix+".json")) {
            return (ObjectNode)mapper.readTree(input);
        }
    }
    private Object read(String type,ObjectNode node) throws Exception {
        byte[] bytes=mapper.writeValueAsBytes(node);
        return type.equals("PaymentSucceeded") ? payment.read(bytes) : refund.read(bytes);
    }
}
