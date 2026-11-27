package com.qsx.appoint.appointment.application;

/**
 * 预约暂占创建接口：生成预约单及有效占用，但不创建支付单或调用支付渠道。
 * <p>由协调层把本操作与支付建单放进同一数据库事务，保证任一失败整笔回滚。
 */
public interface AppointmentHoldService {
    /**
     * 为今天至后天的合法场次暂占一个名额，并保存预约和凭证快照。
     * <p>库存端口按设置→场次占号，随后锁定就诊人；锁后校验今天至后天及场次开始前十五分钟截止，并保存准入时间。
     * @param command 账号、医生、日期、场次类型和就诊人
     * @return 新建暂占及费用/截止快照，不为null；尚未完成支付建单
     * @throws org.springframework.transaction.IllegalTransactionStateException 没有外层事务
     * @throws com.qsx.appoint.common.BusinessException 日期、场次、就诊人资格或有效占用约束不满足
     */
    AppointmentHold createHold(CreateAppointmentHoldCommand command);
}

