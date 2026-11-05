package com.qsx.appoint.catalog.model;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

import java.time.LocalTime;
import java.util.Locale;

public enum AppointmentSession {
    MORNING_FIRST(LocalTime.of(8, 0), LocalTime.of(10, 0)),
    MORNING_SECOND(LocalTime.of(10, 30), LocalTime.of(12, 0)),
    AFTERNOON_FIRST(LocalTime.of(14, 0), LocalTime.of(16, 0)),
    AFTERNOON_SECOND(LocalTime.of(16, 30), LocalTime.of(18, 0));

    private final LocalTime startTime;
    private final LocalTime endTime;

    AppointmentSession(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalTime startTime() {
        return startTime;
    }

    public LocalTime endTime() {
        return endTime;
    }

    public static AppointmentSession require(String raw) {
        try {
            return valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
