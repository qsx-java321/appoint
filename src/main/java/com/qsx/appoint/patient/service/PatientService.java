package com.qsx.appoint.patient.service;

import com.qsx.appoint.patient.dto.CreatePatientRequest;
import com.qsx.appoint.patient.dto.PatientResponse;
import com.qsx.appoint.patient.dto.UpdatePatientRequest;

import java.util.List;

public interface PatientService {
    PatientResponse create(Long userId, CreatePatientRequest request);
    List<PatientResponse> listOwned(Long userId);
    PatientResponse getOwned(Long userId, Long patientId);
    PatientResponse updateOwned(Long userId, Long patientId, UpdatePatientRequest request);
    PatientResponse setEnabled(Long userId, Long patientId, boolean enabled);
}
