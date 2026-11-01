package com.qsx.appoint.catalog.controller;

import com.qsx.appoint.catalog.dto.*;
import com.qsx.appoint.catalog.service.ScheduleManagementService;
import com.qsx.appoint.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/schedules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ScheduleManagementController {
    private final ScheduleManagementService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AdminScheduleResponse> create(Authentication authentication,
                                                @Valid @RequestBody CreateScheduleRequest request) {
        return Result.success(service.create((Long) authentication.getPrincipal(), request));
    }

    @PatchMapping("/{id}")
    public Result<AdminScheduleResponse> update(@PathVariable Long id,
                                                @RequestBody UpdateScheduleRequest request) {
        return Result.success(service.update(id, request));
    }

    @PostMapping("/{id}/inventory-adjustments")
    public Result<AdminScheduleResponse> adjust(Authentication authentication, @PathVariable Long id,
                                                @Valid @RequestBody AdjustScheduleInventoryRequest request) {
        return Result.success(service.adjustInventory((Long) authentication.getPrincipal(), id, request));
    }

    @PostMapping("/{id}/close")
    public Result<AdminScheduleResponse> close(Authentication authentication, @PathVariable Long id) {
        return Result.success(service.close((Long) authentication.getPrincipal(), id));
    }
}
