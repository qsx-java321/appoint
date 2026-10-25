package com.qsx.appoint.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalTime;
@Getter @AllArgsConstructor public class ScheduleResponse { private final Long id; private final LocalDate appointmentDate; private final LocalTime startTime; private final LocalTime endTime; private final String status; }
