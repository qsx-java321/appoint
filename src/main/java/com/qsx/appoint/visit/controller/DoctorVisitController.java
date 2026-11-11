package com.qsx.appoint.visit.controller;

import com.qsx.appoint.bookingquery.application.DoctorVisitQueryService;
import com.qsx.appoint.bookingquery.dto.*;
import com.qsx.appoint.catalog.model.AppointmentSession;
import com.qsx.appoint.common.Result;
import com.qsx.appoint.visit.application.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/doctor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorVisitController {
    private final DoctorVisitQueryService queries;
    private final VisitService visits;

    @GetMapping("/sessions")
    public Result<List<DoctorSessionResponse>> listSessions(Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(queries.listSessions((Long) authentication.getPrincipal(), date));
    }
    @GetMapping("/appointments")
    public Result<List<DoctorAppointmentRowResponse>> listAppointments(Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String sessionType) {
        return Result.success(queries.listAppointments((Long) authentication.getPrincipal(), date, AppointmentSession.require(sessionType)));
    }
    @GetMapping("/appointments/{id}")
    public Result<DoctorAppointmentDetailResponse> getAppointment(Authentication authentication, @PathVariable Long id) {
        return Result.success(queries.getAppointment((Long) authentication.getPrincipal(), id));
    }
    @PostMapping("/appointments/{id}/start-visit")
    public Result<DoctorAppointmentDetailResponse> startVisit(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        visits.startVisit(userId, id);
        return Result.success(queries.getAppointment(userId, id));
    }
    @PostMapping("/appointments/{id}/complete-visit")
    public Result<DoctorAppointmentDetailResponse> completeVisit(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        visits.completeVisit(userId, id);
        return Result.success(queries.getAppointment(userId, id));
    }
}
