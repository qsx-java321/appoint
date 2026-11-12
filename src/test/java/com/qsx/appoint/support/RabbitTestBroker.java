package com.qsx.appoint.support;

import org.testcontainers.containers.RabbitMQContainer;

/** Broker故障注入有时间上限并检查退出码；失败不能被当成成功的故障窗口。 */
public final class RabbitTestBroker {
    private RabbitTestBroker() { }
    public static void command(RabbitMQContainer rabbit, String action) throws Exception {
        var result = rabbit.execInContainer("timeout", "30s", "rabbitmqctl", action);
        if (result.getExitCode() != 0) throw new AssertionError("rabbitmqctl " + action + "失败，exit=" + result.getExitCode());
    }
}
