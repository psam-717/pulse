package com.example.demo.dto;

/**
 * Patient insurance record — GET/PUT /api/patients/me/insurance.
 * Field names match pulse-mobile {@code insurance-store.ts} InsuranceDetails
 * verbatim (ARCHITECTURE.md §5.2 / §8 P2). The stored photo URL is exposed
 * as {@code cardPhotoUri}; the client obtains that URL from
 * {@code POST /api/uploads/images} and sends it back on PUT.
 */
public record InsuranceDetailsResponse(
        String scheme,
        String membershipNumber,
        String cardholderName,
        String expiryDate,
        String cardPhotoUri
) {}
