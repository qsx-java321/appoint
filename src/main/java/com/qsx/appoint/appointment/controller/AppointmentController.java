package com.qsx.appoint.appointment.controller;

import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 当前用户提交和查询预约单的入口。 */
@RestController @RequestMapping("/api/v1/appointments") @RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService appointmentService;
    @PostMapping 
    @ResponseStatus(HttpStatus.CREATED)
    public Result<CreateAppointmentResponse> create(Authentication authentication, @Valid @RequestBody CreateAppointmentRequest request)
     { 
        return Result.success(appointmentService.create((Long) authentication.getPrincipal(), request));
     }
    @GetMapping("/{id}") 
    public Result<AppointmentResponse> get(Authentication authentication, @PathVariable Long id) 
        {
             return Result.success(appointmentService.getOwned((Long) authentication.getPrincipal(), id)); 
        }
    @GetMapping("/my") 
    public Result<List<AppointmentResponse>> mine(Authentication authentication) 
        {
         return Result.success(appointmentService.listMine((Long) authentication.getPrincipal())); 
        }
}
