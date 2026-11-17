package com.qsx.appoint.catalog.controller;

import com.qsx.appoint.catalog.dto.DoctorAccountListResponse;
import com.qsx.appoint.catalog.service.DoctorAccountQueryService;
import com.qsx.appoint.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/doctor-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DoctorAccountQueryController {
    private final DoctorAccountQueryService accounts;

    @GetMapping
    public Result<DoctorAccountListResponse> listAccounts() {
        return Result.success(accounts.listAccounts());
    }
}
