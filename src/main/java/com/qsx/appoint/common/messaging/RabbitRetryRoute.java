package com.qsx.appoint.common.messaging;

/** 由具体监听器装配的受控重试目标；不从用户输入生成，不依赖业务模块配置。 */
public record RabbitRetryRoute(String exchange, String routingKey, String attemptHeader) {}
