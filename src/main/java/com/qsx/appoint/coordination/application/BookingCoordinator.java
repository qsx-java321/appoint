package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.BookingTokenResponse;

/**
 * 预约创建用例入口，负责请求幂等及重放，协调预约暂占与支付建单。
 * 首次创建由同库事务完成，同键重放返回原创建回执。
 */
public interface BookingCoordinator {
    BookingTokenResponse issueToken(Long userId);

    /**
     * 按账号和幂等键创建预约，或返回已经完成的同内容请求回执。
     * @param userId 当前已认证账号用户ID
     * @param idempotencyKey 后端签发的UUID令牌；同一预约意图重试须沿用原令牌
     * @param request 医生、日期、四场次类型和就诊人；不接受用户指定内部场次ID
     * @return 首次回执、重放回执或正在提交
     * @throws com.qsx.appoint.common.BusinessException 键非法、同键异内容、原登记未完整完成或创建资格不满足
     */
    AppointmentCreationResult create(Long userId, String idempotencyKey, CreateAppointmentRequest request);
}

