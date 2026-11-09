package com.qsx.appoint.appointment.domain;

public enum AppointmentCloseReason {
    USER_CANCELLED(AppointmentStatus.CANCELLED), PAYMENT_EXPIRED(AppointmentStatus.EXPIRED);
    private final AppointmentStatus status;
    AppointmentCloseReason(AppointmentStatus status) { this.status = status; }
    public AppointmentStatus status() { return status; }
}
