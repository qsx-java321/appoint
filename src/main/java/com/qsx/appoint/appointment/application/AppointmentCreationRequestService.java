package com.qsx.appoint.appointment.application;

import java.time.LocalDateTime;

/**
 * 预约创建幂等登记接口，由同库创建事务登记和完成，由协调入口在冲突回滚后查询重放。
 * <p>register和complete要求调用者已有事务；唯一键范围为账号用户加规范化幂等键。
 */
public interface AppointmentCreationRequestService {
    /**
     * 登记本次预约创建意图，尚不表示创建成功。
     * @param userId 发起创建的账号用户ID
     * @param idempotencyKey 已规范化为标准UUID文本的请求键
     * @param requestHash 医生、日期、场次类型、就诊人规范化后的摘要
     * @param createdAt 外层创建事务的服务端时间
     * @return 新登记记录ID
     * @throws org.springframework.dao.DuplicateKeyException 同账号同键已存在，调用者应在事务回滚后查询重放
     * @throws org.springframework.transaction.IllegalTransactionStateException 没有外层事务
     */
    Long register(Long userId, String idempotencyKey, String requestHash, LocalDateTime createdAt);

    /**
     * 将幂等登记关联到创建成功的预约，并保存首次响应。
     * @param requestId 本事务register生成的登记ID
     * @param appointmentOrderId 同事务新建的预约单ID
     * @param responseJson 首次创建响应的JSON，不含Token或渠道密钥
     * @throws com.qsx.appoint.common.BusinessException 完成登记未命中一行
     * @throws org.springframework.transaction.IllegalTransactionStateException 没有外层事务
     */
    void complete(Long requestId, Long appointmentOrderId, String responseJson);

    /**
     * 按账号和幂等键查找已有登记及关联预约，供唯一键冲突后判断重放。
     * @param userId 账号用户ID，构成幂等隔离范围
     * @param idempotencyKey 规范化后的UUID键
     * @return 重放事实；无登记返回null，登记未完整完成时completed=false
     */
    AppointmentCreationReplay find(Long userId, String idempotencyKey);
}

