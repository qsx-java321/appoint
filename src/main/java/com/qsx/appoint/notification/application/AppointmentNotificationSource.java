package com.qsx.appoint.notification.application;

/** 由预约模块提供的只读快照端口；通知模块不读取就诊人或目录当前表。 */
public interface AppointmentNotificationSource {
    /**
     * 按预约单读取事件生成时的凭证快照。
     * @param appointmentOrderId 预约单主键
     * @return 快照；预约不存在返回null
     */
    AppointmentNotificationSnapshot find(Long appointmentOrderId);
}
