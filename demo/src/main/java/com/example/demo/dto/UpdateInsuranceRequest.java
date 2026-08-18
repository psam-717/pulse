package com.example.demo.dto;

/**
 * Upsert body for PUT /api/patients/me/insurance.
 * All fields optional; only provided (non-null) fields are applied.
 * {@code cardPhotoUri} is the URL returned by POST /api/uploads/images.
 */
public record UpdateInsuranceRequest(
        String scheme,
        String membershipNumber,
        String cardholderName,
        String expiryDate,
        String cardPhotoUri
) {}
