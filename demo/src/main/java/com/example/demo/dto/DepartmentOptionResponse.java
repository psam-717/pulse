package com.example.demo.dto;

import java.math.BigDecimal;

/**
 * Department picker option — GET /api/mobile/hospitals/{id}/departments.
 * Shape from ARCHITECTURE.md §8 P2 (DepartmentOptionResponse).
 */
public record DepartmentOptionResponse(
        Long id,
        String name,
        BigDecimal consultationFee,
        String description,
        boolean hasDoctors
) {}
