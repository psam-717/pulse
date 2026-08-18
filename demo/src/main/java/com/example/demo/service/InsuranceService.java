package com.example.demo.service;

import com.example.demo.dto.InsuranceDetailsResponse;
import com.example.demo.dto.UpdateInsuranceRequest;
import com.example.demo.model.Patient;
import com.example.demo.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Patient-scoped insurance record (ARCHITECTURE.md §8 P2 / G3).
 * Sensitive PII — Ghana Data Protection Act, 2012 (Act 843): only the
 * owning patient (JWT subject) may read or write this record.
 */
@Service
public class InsuranceService {

    private final PatientRepository patientRepository;

    public InsuranceService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public InsuranceDetailsResponse getInsurance(Long patientId) {
        return toResponse(requirePatient(patientId));
    }

    @Transactional
    public InsuranceDetailsResponse upsertInsurance(Long patientId, UpdateInsuranceRequest req) {
        Patient p = requirePatient(patientId);
        if (req == null) {
            throw new IllegalArgumentException(
                    "Request body is required. Send scheme, membershipNumber, cardholderName, expiryDate.");
        }
        if (req.scheme() != null) {
            String scheme = req.scheme().trim();
            p.setInsuranceScheme(scheme.isEmpty() ? null : scheme);
        }
        if (req.membershipNumber() != null) {
            p.setInsuranceMembershipNumber(req.membershipNumber().trim());
        }
        if (req.cardholderName() != null) {
            p.setInsuranceCardholderName(req.cardholderName().trim());
        }
        if (req.expiryDate() != null) {
            if (req.expiryDate().isBlank()) {
                p.setInsuranceExpiryDate(null);
            } else {
                try {
                    p.setInsuranceExpiryDate(LocalDate.parse(req.expiryDate().trim()));
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException(
                            "expiryDate must be an ISO date (yyyy-MM-dd), e.g. 2027-01-31.");
                }
            }
        }
        if (req.cardPhotoUri() != null) {
            String uri = req.cardPhotoUri().trim();
            p.setInsuranceCardPhotoUrl(uri.isEmpty() ? null : uri);
        }
        return toResponse(patientRepository.save(p));
    }

    private Patient requirePatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
    }

    private static InsuranceDetailsResponse toResponse(Patient p) {
        String membership = p.getInsuranceMembershipNumber();
        String cardholder = p.getInsuranceCardholderName();
        return new InsuranceDetailsResponse(
                p.getInsuranceScheme(),
                membership != null ? membership : "",
                cardholder != null ? cardholder : "",
                p.getInsuranceExpiryDate() != null ? p.getInsuranceExpiryDate().toString() : null,
                p.getInsuranceCardPhotoUrl());
    }
}
