package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import java.time.LocalDateTime;

/** 完整预约生命周期；锁定及写入必须参加调用者事务。 */
public interface AppointmentLifecycle {
    AppointmentLifecycleCase find(Long appointmentOrderId);
    /** 内部按场次→预约加锁，结果仅供当前事务使用。 */
    AppointmentLifecycleCase lock(Long appointmentOrderId);
    AppointmentConfirmation confirm(AppointmentLifecycleCase appointment);
    void closePending(AppointmentLifecycleCase appointment, AppointmentCloseReason reason, LocalDateTime closedAt);
    void cancelConfirmed(AppointmentLifecycleCase appointment, LocalDateTime cancelledAt);
}
