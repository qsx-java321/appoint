package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;

/** 当前用户可查看的预约单信息。 */
@Getter @AllArgsConstructor
public class AppointmentResponse {
    private final Long id;
    private final String appointmentNo;
    private final Long scheduleId;
    private final LocalDate appointmentDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String status;
    private final LocalDateTime createdAt;
    private final Long paymentOrderId;
    private final String paymentStatus;
    private final LocalDateTime paymentExpiresAt;
    private final LocalDateTime closedAt;
    private final LocalDateTime serverTime;
    private final boolean paymentExpired;
    private final boolean canPay;
    private final boolean canCancel;
    private final AppointmentPatientResponse patient;
    private final AppointmentDoctorResponse doctor;
}
