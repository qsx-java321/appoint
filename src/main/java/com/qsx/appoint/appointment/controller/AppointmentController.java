package com.qsx.appoint.appointment.controller;

import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.common.Result;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 当前用户提交和查询预约单的入口。 */
@RestController @RequestMapping("/api/v1/appointments") @RequiredArgsConstructor
public class AppointmentController {
    private static final String REPLAY_MESSAGE = "该请求已创建预约，请查询详情获取当前状态";
    private final AppointmentService appointmentService;
    private final BookingCoordinator bookingCoordinator;
    private final BookingLifecycleCoordinator lifecycleCoordinator;
    @PostMapping
    public ResponseEntity<Result<AppointmentCreationResult>> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentCreationResult response = bookingCoordinator.create(
                (Long) authentication.getPrincipal(), idempotencyKey, request);
        if (response instanceof ReplayedAppointmentResponse) {
            return ResponseEntity.ok(Result.success(REPLAY_MESSAGE, response));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(response));
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
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Result<AppointmentResponse>> cancel(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        boolean refundPending = lifecycleCoordinator.cancelOwned(userId, id);
        AppointmentResponse response = appointmentService.getOwned(userId, id);
        Result<AppointmentResponse> result = refundPending
                ? Result.success("退款申请已受理", response)
                : Result.success(response);
        return ResponseEntity.status(refundPending ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result);
    }
}
