package com.qsx.appoint.bookingquery.dto;
import java.time.LocalDateTime;
public record DoctorVisitProgressResponse(String status, LocalDateTime checkedInAt,
        LocalDateTime startedAt, LocalDateTime completedAt) {}
