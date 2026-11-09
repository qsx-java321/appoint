package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import java.time.LocalDateTime;

public interface AppointmentLifecycleOperations {
    AppointmentLifecycleView findAppointment(Long appointmentOrderId);
    ScheduleLifecycleView lockSchedule(Long scheduleId);
    AppointmentLifecycleView lockAppointment(Long appointmentOrderId);
    void confirmInventory(Long scheduleId, Integer visitNumber);
    void confirmAppointment(Long appointmentOrderId, Integer visitNumber);
    void releaseHeldInventory(Long scheduleId);
    void closePendingAppointment(Long appointmentOrderId, AppointmentCloseReason reason, LocalDateTime closedAt);
    void releaseConfirmedInventory(Long scheduleId);
    void cancelConfirmedAppointment(Long appointmentOrderId, LocalDateTime closedAt);
}
