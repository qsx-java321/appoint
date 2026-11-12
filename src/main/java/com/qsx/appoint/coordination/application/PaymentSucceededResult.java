package com.qsx.appoint.coordination.application;

/** 消息用例的本地处理结果；不等同于传输确认或新的资金状态。 */
public enum PaymentSucceededResult { DUPLICATE, ALREADY_CONFIRMED, CONFIRMED, REFUND_REQUIRED }
