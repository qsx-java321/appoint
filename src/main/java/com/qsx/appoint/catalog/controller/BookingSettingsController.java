package com.qsx.appoint.catalog.controller;

import com.qsx.appoint.catalog.dto.BookingSettingsResponse;
import com.qsx.appoint.catalog.dto.UpdateBookingSettingsRequest;
import com.qsx.appoint.catalog.service.BookingSettingsService;
import com.qsx.appoint.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/booking-settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BookingSettingsController {
    private final BookingSettingsService service;

    @GetMapping
    public Result<BookingSettingsResponse> get() {
        return Result.success(service.get());
    }

    @PutMapping
    public Result<BookingSettingsResponse> update(@Valid @RequestBody UpdateBookingSettingsRequest request) {
        return Result.success(service.update(request));
    }
}
