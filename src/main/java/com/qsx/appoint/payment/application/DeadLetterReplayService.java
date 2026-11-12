package com.qsx.appoint.payment.application;

/** 受控死信重放用例，不暴露物理队列或 AMQP 类型，不绕过业务幂等。 */
public interface DeadLetterReplayService {
    /**
     * 最多转发一条原消息，确认转发后才确认源投递；失败保留原消息并传播异常。
     * 不重置Outbox FAILED，不写Inbox或资金；退款结果重放不代表用户通知完成。
     * @param target 三种已登记的逻辑目标，不能为空
     * @return 空队列为EMPTY，已转发为REPLAYED；正文缺ID仍可返回可空eventId
     */
    ReplayResult replayOne(ReplayTarget target);
}
