package com.qsx.appoint.appointment.service;

import com.qsx.appoint.appointment.dto.*;
import java.util.List;

/**
 * 预约详情与本人列表的只读查询契约，实际实现归属bookingquery。
 * <p>为保持现有Controller依赖保留接口位置，不用于执行预约写入。
 */
public interface AppointmentService {
    /**
     * 查询本人预约详情，归属校验先于支付关联检查。
     * @param userId 已认证账号ID
     * @param orderId 预约单ID
     * @return 当前详情，不为null
     * @throws com.qsx.appoint.common.BusinessException 不存在为RESOURCE_NOT_FOUND，非本人为FORBIDDEN，资金关联不一致为INTERNAL_ERROR
     */
    AppointmentResponse getOwned(Long userId, Long orderId);

    /**
     * 查询本人全部预约，使用同一次秒精度serverTime计算各行展示标记。
     * @param userId 已认证账号ID
     * @return 按预约ID降序的详情列表，无记录为空列表；任一非法组合会使本次查询失败
     */
    List<AppointmentResponse> listMine(Long userId);
}
