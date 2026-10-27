package com.qsx.appoint.patient.controller;

import com.qsx.appoint.common.Result;
import com.qsx.appoint.patient.dto.CreatePatientRequest;
import com.qsx.appoint.patient.dto.PatientResponse;
import com.qsx.appoint.patient.dto.UpdatePatientRequest;
import com.qsx.appoint.patient.dto.UpdatePatientStatusRequest;
import com.qsx.appoint.patient.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {
    private final PatientService patientService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PatientResponse> create(Authentication authentication,
                                          @Valid @RequestBody CreatePatientRequest request) {
        return Result.success(patientService.create((Long) authentication.getPrincipal(), request));
    }

    @GetMapping
    public Result<List<PatientResponse>> list(Authentication authentication) {
        return Result.success(patientService.listOwned((Long) authentication.getPrincipal()));
    }

    @GetMapping("/{id}")
    public Result<PatientResponse> get(Authentication authentication, @PathVariable Long id) {
        return Result.success(patientService.getOwned((Long) authentication.getPrincipal(), id));
    }

    @PatchMapping("/{id}")
    public Result<PatientResponse> update(Authentication authentication, @PathVariable Long id,
                                          @Valid @RequestBody UpdatePatientRequest request) {
        return Result.success(patientService.updateOwned((Long) authentication.getPrincipal(), id, request));
    }

    @PatchMapping("/{id}/status")
    public Result<PatientResponse> setStatus(Authentication authentication, @PathVariable Long id,
                                             @Valid @RequestBody UpdatePatientStatusRequest request) {
        return Result.success(patientService.setEnabled((Long) authentication.getPrincipal(), id,
                request.getEnabled()));
    }
}
