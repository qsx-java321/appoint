package com.qsx.appoint.bookingquery.dto;
import java.time.LocalDateTime;
public record DoctorAppointmentRowResponse(Long appointmentId, Integer visitNumber, String patientName,
        String visitStatus, LocalDateTime checkedInAt, boolean canStartVisit, boolean canCompleteVisit) {}
