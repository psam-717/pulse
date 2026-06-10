package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DepartmentRequest(

        @NotBlank(message = "Department name is required")
        String name,

        @NotBlank(message = "Abbreviation is required")
        String abbreviation,

        String description,

        @NotNull(message = "Consultation fee is required")
        BigDecimal consultationFee,

        Long parentDepartmentId
) {}