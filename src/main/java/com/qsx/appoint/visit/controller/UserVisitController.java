package com.qsx.appoint.visit.controller;

import com.qsx.appoint.appointment.dto.AppointmentResponse;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.common.Result;
import com.qsx.appoint.visit.application.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class UserVisitController {
    private final VisitService visits;
    private final AppointmentService appointments;

    @PostMapping("/{id}/check-in")
    public Result<AppointmentResponse> checkIn(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        visits.checkIn(userId, id);
        return Result.success(appointments.getOwned(userId, id));
    }
}
