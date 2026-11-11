package com.qsx.appoint.bookingquery.dto;
import java.time.LocalDate;
import java.time.LocalTime;
public record DoctorSessionResponse(LocalDate appointmentDate, String sessionType, LocalTime startTime,
        LocalTime endTime, int confirmedCount, int notCheckedInCount, int waitingCount,
        int inProgressCount, int completedCount) {}
