package com.example.demo.dto;

import java.util.List;

/**
 * Mobile hospital discovery card — GET /api/mobile/hospitals.
 * Shape from ARCHITECTURE.md §8 P2 (HospitalCardResponse).
 */
public record HospitalCardResponse(
        String id,
        String name,
        String location,
        double rating,
        String reviews,
        String image,
        Double distanceKm,
        String waitTime,
        String status,
        List<String> specialties
) {}
