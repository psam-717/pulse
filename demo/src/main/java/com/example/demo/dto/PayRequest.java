package com.example.demo.dto;

import java.util.List;

/**
 * POST /api/patients/me/payments — open one Aza checkout for the total fee.
 */
public record PayRequest(
        List<Long> bookingIds,
        Long methodId
) {}
