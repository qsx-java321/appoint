package com.qsx.appoint.payment.application.archfixture;

import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import java.util.List;

/** 故意违规的测试契约：Entity 不能藏在入参record/泛型中跨接口传播。 */
public interface LeakingInputContract {
    record Hidden(List<PaymentOutboxEvent> events) {}
    void accept(Hidden input);
}
