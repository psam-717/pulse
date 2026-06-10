package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record WorkingHoursEntry(
        @NotNull Integer dayOfWeek,
        @NotNull LocalTime openTime,
        @NotNull LocalTime closeTime,
        boolean isClosed
) {}