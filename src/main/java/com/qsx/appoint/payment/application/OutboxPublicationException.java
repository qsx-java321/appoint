package com.qsx.appoint.payment.application;

/** 未获可靠发布确认。Broker 可能已接收；重试必须沿用原ID与正文，不能推断消费结果。 */
public class OutboxPublicationException extends RuntimeException {
    public OutboxPublicationException(String message, Throwable cause) { super(message,cause); }
}
