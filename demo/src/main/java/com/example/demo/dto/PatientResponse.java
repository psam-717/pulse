package com.example.demo.dto;

import com.example.demo.model.Patient;
import com.example.demo.model.QueueEntry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Web dashboard Patient shape (BACKEND_SPEC.md §5.5) — mirrors the
 * frontend's lib/types/patients.ts. Clinical fields are record-keeping only.
 */
public record PatientResponse(
        String id,
        String patientNumber,
        String name,
        String dateOfBirth,
        String gender,
        String phone,
        String email,
        String address,
        String registeredAt,
        String bloodType,
        List<String> allergies,
        List<Medication> currentMedications,
        Vitals latestVitals,
        CurrentVisit currentVisit
) {

    public record Medication(String name, String dose, String frequency) {}

    public record Vitals(String bloodPressure, String temperature,
                         String pulse, String weight, String recordedAt) {}

    public record CurrentVisit(String status, String departmentId,
                               String departmentName, String since,
                               String appointmentId) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PatientResponse from(Patient p, QueueEntry currentQueueEntry,
                                       String departmentName) {
        CurrentVisit visit = null;
        if (currentQueueEntry != null) {
            visit = new CurrentVisit(
                    currentQueueEntry.getStatus().name().toLowerCase(),
                    currentQueueEntry.getDepartmentId(),
                    departmentName,
                    String.valueOf(currentQueueEntry.getCheckInAt()),
                    null);
        }
        return new PatientResponse(
                String.valueOf(p.getId()),
                p.getPatientNumber(),
                p.getFirstName() + " " + p.getLastName(),
                p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : null,
                p.getGender() != null ? p.getGender().name().toLowerCase() : "other",
                p.getPhone(),
                p.getEmail(),
                p.getAddress(),
                String.valueOf(p.getRegisteredAt() != null
                        ? p.getRegisteredAt() : LocalDateTime.now()),
                p.getBloodType(),
                parseAllergies(p.getAllergies()),
                parseMedications(p.getCurrentMedications()),
                parseVitals(p.getLatestVitals()),
                visit);
    }

    private static List<String> parseAllergies(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String a : raw.split(",")) {
            String t = a.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static List<Medication> parseMedications(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            List<Medication> out = new ArrayList<>();
            JsonNode arr = MAPPER.readTree(raw);
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    out.add(new Medication(
                            text(n, "name"), text(n, "dose"), text(n, "frequency")));
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Vitals parseVitals(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode n = MAPPER.readTree(raw);
            if (n == null || !n.isObject()) return null;
            return new Vitals(
                    text(n, "bloodPressure"), text(n, "temperature"),
                    text(n, "pulse"), text(n, "weight"), text(n, "recordedAt"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
