package com.qsx.appoint.appointment.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** HTTP 200 与首次创建使用同一份回执数据。 */
public record ReplayedAppointmentResponse(@JsonUnwrapped CreateAppointmentResponse snapshot)
        implements AppointmentCreationResult {}
