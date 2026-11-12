package com.qsx.appoint.payment.application;

/** 受控重放的三个逻辑目标，物理队列映射只由 Rabbit 适配器掌握。 */
public enum ReplayTarget { PAYMENT_SUCCEEDED, REFUND_REQUESTED, REFUND_RESULT }
