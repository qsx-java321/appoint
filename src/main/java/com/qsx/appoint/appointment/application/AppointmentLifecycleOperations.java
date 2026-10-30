package com.qsx.appoint.appointment.application;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentLifecycleOperations {
    AppointmentLifecycleView findAppointment(Long appointmentOrderId);
    ScheduleLifecycleView lockSchedule(Long scheduleId);
    AppointmentLifecycleView lockAppointment(Long appointmentOrderId);
    List<AppointmentLifecycleView> lockPendingAppointments(Long scheduleId);
    void confirmInventory(Long scheduleId, Integer visitNumber);
    void confirmAppointment(Long appointmentOrderId, Integer visitNumber);
    void releaseHeldInventory(Long scheduleId);
    void closePendingAppointment(Long appointmentOrderId, String status, LocalDateTime closedAt);
    void releaseConfirmedInventory(Long scheduleId);
    void cancelConfirmedAppointment(Long appointmentOrderId, LocalDateTime closedAt);
    void closeSchedule(Long scheduleId, Long operatorUserId, LocalDateTime closedAt);
}
