package com.qsx.appoint.catalog.controller;

import com.qsx.appoint.catalog.dto.BindDoctorAccountRequest;
import com.qsx.appoint.catalog.dto.DoctorAccountResponse;
import com.qsx.appoint.catalog.service.DoctorAccountService;
import com.qsx.appoint.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/doctors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DoctorAccountController {
    private final DoctorAccountService accounts;

    @PutMapping("/{id}/account")
    public Result<DoctorAccountResponse> bindAccount(@PathVariable Long id,
                                                    @Valid @RequestBody BindDoctorAccountRequest request) {
        return Result.success(accounts.bindAccount(id, request.userId()));
    }
}
