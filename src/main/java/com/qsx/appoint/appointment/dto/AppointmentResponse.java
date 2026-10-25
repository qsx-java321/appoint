package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

/** 当前用户可查看的预约单信息。 */
@Getter @AllArgsConstructor
public class AppointmentResponse {
    private final Long id;
    private final String appointmentNo;
    private final Long scheduleId;
    private final String status;
    private final LocalDateTime createdAt;
    private final Long paymentOrderId;
    private final String paymentStatus;
    private final AppointmentDoctorResponse doctor;
}
