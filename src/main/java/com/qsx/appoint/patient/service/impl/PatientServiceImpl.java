package com.qsx.appoint.patient.service.impl;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.patient.dto.CreatePatientRequest;
import com.qsx.appoint.patient.dto.PatientResponse;
import com.qsx.appoint.patient.dto.UpdatePatientRequest;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.patient.service.PatientService;
import com.qsx.appoint.patient.service.ResidentIdCard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {
    private static final String RESIDENT_ID_CARD = "RESIDENT_ID_CARD";
    private final PatientMapper patientMapper;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public PatientResponse create(Long userId, CreatePatientRequest request) {
        Patient patient = new Patient();
        patient.setUserId(userId);
        apply(patient, request.getName(), request.getDocumentType(), request.getDocumentNo(),
                request.getContactPhone(), request.getContactEmail());
        patient.setEnabled(true);
        LocalDateTime now = now();
        patient.setCreatedAt(now);
        patient.setUpdatedAt(now);
        try {
            patientMapper.insert(patient);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PATIENT_DOCUMENT_EXISTS);
        }
        return response(patient);
    }

    @Override
    public List<PatientResponse> listOwned(Long userId) {
        return patientMapper.selectByUserId(userId).stream().map(this::response).toList();
    }

    @Override
    public PatientResponse getOwned(Long userId, Long patientId) {
        return response(requireOwned(userId, patientMapper.selectById(patientId)));
    }

    @Override
    @Transactional
    public PatientResponse updateOwned(Long userId, Long patientId, UpdatePatientRequest request) {
        Patient patient = requireOwned(userId, patientMapper.selectByIdForUpdate(patientId));
        apply(patient,
                request.getName() == null ? patient.getName() : request.getName(),
                request.getDocumentType() == null ? patient.getDocumentType() : request.getDocumentType(),
                request.getDocumentNo() == null ? patient.getDocumentNo() : request.getDocumentNo(),
                request.getContactPhone() == null ? patient.getContactPhone() : request.getContactPhone(),
                request.getContactEmail() == null ? patient.getContactEmail() : request.getContactEmail());
        patient.setUpdatedAt(now());
        try {
            patientMapper.updateById(patient);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PATIENT_DOCUMENT_EXISTS);
        }
        return response(patient);
    }

    @Override
    @Transactional
    public PatientResponse setEnabled(Long userId, Long patientId, boolean enabled) {
        Patient patient = requireOwned(userId, patientMapper.selectByIdForUpdate(patientId));
        if (!Boolean.valueOf(enabled).equals(patient.getEnabled())) {
            patient.setEnabled(enabled);
            patient.setUpdatedAt(now());
            patientMapper.updateById(patient);
        }
        return response(patient);
    }

    private void apply(Patient patient, String name, String documentType, String documentNo,
                       String contactPhone, String contactEmail) {
        patient.setName(required(name));
        String type = required(documentType);
        if (!RESIDENT_ID_CARD.equals(type)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        patient.setDocumentType(type);
        patient.setDocumentNo(ResidentIdCard.normalize(documentNo));
        patient.setContactPhone(required(contactPhone));
        String email = required(contactEmail).toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        patient.setContactEmail(email);
    }

    private Patient requireOwned(Long userId, Patient patient) {
        if (patient == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!patient.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return patient;
    }

    private String required(String value) {
        if (value == null || value.trim().isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return value.trim();
    }

    private PatientResponse response(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getName(), patient.getDocumentType(),
                ResidentIdCard.mask(patient.getDocumentNo()), patient.getContactPhone(), patient.getContactEmail(),
                Boolean.TRUE.equals(patient.getEnabled()), patient.getCreatedAt(), patient.getUpdatedAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}
