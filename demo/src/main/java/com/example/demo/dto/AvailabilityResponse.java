package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Department availability window — GET /api/mobile/departments/{id}/availability.
 * Shape matches pulse-mobile {@code fetchMockAvailability} / HospitalAvailability
 * (ARCHITECTURE.md §5.2 / §8 P2) exactly.
 *
 * {@code time} is ISO LocalTime ({@code "09:00"}); the mobile client formats
 * the display string ({@code "09:00 AM"}).
 */
public record AvailabilityResponse(
        List<String> closedDates,
        List<String> fullDates,
        Map<String, DaySlots> slots
) {
    public record DaySlots(
            @JsonProperty("MORNING") List<SlotItem> MORNING,
            @JsonProperty("AFTERNOON") List<SlotItem> AFTERNOON
    ) {}

    public record SlotItem(String time, boolean available) {}
}
