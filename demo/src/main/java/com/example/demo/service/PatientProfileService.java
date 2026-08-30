package com.example.demo.service;

import com.example.demo.dto.MedicalProfileResponse;
import com.example.demo.dto.PatientProfileResponse;
import com.example.demo.dto.UpdateMedicalProfileRequest;
import com.example.demo.util.GhanaPhoneValidator;
import com.example.demo.dto.UpdatePatientProfileRequest;
import com.example.demo.dto.AddVitalsRequest;
import com.example.demo.model.Patient;
import com.example.demo.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Patient self-service profile + medical profile (ARCHITECTURE.md §8 P1).
 * Serves the mobile app: GET/PATCH /api/patients/me, /me/medical, /me/vitals.
 *
 * The structured mobile fields (allergiesJson/conditionsJson/medicationsJson/
 * vitalsJson + emergency contact) are kept in sync with the legacy flat web
 * fields (allergies comma-string, currentMedications JSON, latestVitals) so
 * the web dashboard contract (BACKEND_SPEC §5.5) never breaks.
 */
@Service
public class PatientProfileService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PatientRepository patientRepository;

    public PatientProfileService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    // ===== Profile =====

    public PatientProfileResponse getProfile(Long patientId) {
        Patient p = requirePatient(patientId);
        return toProfileResponse(p);
    }

    @Transactional
    public PatientProfileResponse updateProfile(Long patientId, UpdatePatientProfileRequest req) {
        Patient p = requirePatient(patientId);
        if (req.firstName() != null && !req.firstName().isBlank()) p.setFirstName(req.firstName().trim());
        if (req.lastName() != null && !req.lastName().isBlank()) p.setLastName(req.lastName().trim());
        if (req.dateOfBirth() != null && !req.dateOfBirth().isBlank()) {
            p.setDateOfBirth(LocalDate.parse(req.dateOfBirth()));
        }
        if (req.gender() != null && !req.gender().isBlank()) {
            p.setGender(mapGender(req.gender()));
        }
        if (req.email() != null && !req.email().isBlank()) p.setEmail(req.email().trim());
        if (req.phone() != null && !req.phone().isBlank()) {
            GhanaPhoneValidator.requireValid(req.phone(), "Phone number");
            patientRepository.findByPhone(req.phone())
                    .filter(existing -> !existing.getId().equals(patientId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "A patient with phone " + req.phone() + " is already registered.");
                    });
            p.setPhone(req.phone().trim());
        }
        if (req.address() != null && !req.address().isBlank()) p.setAddress(req.address().trim());
        if (req.emergencyContact() != null) {
            GhanaPhoneValidator.requireValid(req.emergencyContact().phone(), "Emergency contact phone");
            p.setEmergencyContactName(req.emergencyContact().name());
            p.setEmergencyContactRelationship(req.emergencyContact().relationship());
            p.setEmergencyContactPhone(req.emergencyContact().phone());
        }
        return toProfileResponse(patientRepository.save(p));
    }

    // ===== Medical profile =====

    public MedicalProfileResponse getMedical(Long patientId) {
        Patient p = requirePatient(patientId);
        return toMedicalResponse(p);
    }

    @Transactional
    public MedicalProfileResponse updateMedical(Long patientId, UpdateMedicalProfileRequest req) {
        Patient p = requirePatient(patientId);
        if (req.bloodGroup() != null && !req.bloodGroup().isBlank()) {
            p.setBloodType(req.bloodGroup().trim().toUpperCase());
        }
        if (req.allergies() != null) {
            ArrayNode arr = MAPPER.createArrayNode();
            for (UpdateMedicalProfileRequest.AllergyInput a : req.allergies()) {
                ObjectNode node = arr.addObject();
                node.put("id", newId("al"));
                node.put("label", a.label());
                node.put("type", a.type() != null ? a.type() : "drug");
            }
            p.setAllergiesJson(write(arr));
            // Keep legacy web field in sync: comma-separated labels.
            p.setAllergies(req.allergies().stream()
                    .map(a -> a.label().trim())
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.joining(",")));
        }
        if (req.conditions() != null) {
            ArrayNode arr = MAPPER.createArrayNode();
            for (UpdateMedicalProfileRequest.ConditionInput c : req.conditions()) {
                ObjectNode node = arr.addObject();
                node.put("id", newId("co"));
                node.put("label", c.label());
            }
            p.setConditionsJson(write(arr));
        }
        if (req.medications() != null) {
            ArrayNode arr = MAPPER.createArrayNode();
            for (UpdateMedicalProfileRequest.MedicationInput m : req.medications()) {
                ObjectNode node = arr.addObject();
                node.put("id", newId("me"));
                node.put("name", m.name());
                node.put("dose", m.dose() != null ? m.dose() : "");
            }
            p.setMedicationsJson(write(arr));
            // Keep legacy web field in sync: [{name,dose,frequency:""}]
            try {
                tools.jackson.databind.node.ArrayNode web = MAPPER.createArrayNode();
                for (UpdateMedicalProfileRequest.MedicationInput m : req.medications()) {
                    ObjectNode node = web.addObject();
                    node.put("name", m.name());
                    node.put("dose", m.dose() != null ? m.dose() : "");
                    node.put("frequency", "");
                }
                p.setCurrentMedications(write(web));
            } catch (Exception ignored) {}
        }
        return toMedicalResponse(patientRepository.save(p));
    }

    /** Append a self-logged vitals entry (id + date server-set). Newest first,
     *  matching the mobile store's prepend semantics. */
    @Transactional
    public MedicalProfileResponse addVitals(Long patientId, AddVitalsRequest req) {
        Patient p = requirePatient(patientId);
        ArrayNode arr = parseArray(p.getVitalsJson());
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", newId("vt"));
        node.put("date", LocalDate.now().toString());
        node.put("systolic", nz(req.systolic()));
        node.put("diastolic", nz(req.diastolic()));
        node.put("pulseBpm", nz(req.pulseBpm()));
        node.put("temperatureC", nz(req.temperatureC()));
        node.put("heightCm", nz(req.heightCm()));
        node.put("weightKg", nz(req.weightKg()));
        arr.insert(0, node);   // newest first
        p.setVitalsJson(write(arr));
        // Keep legacy latestVitals in sync with the newest entry.
        p.setLatestVitals(write(node));
        return toMedicalResponse(patientRepository.save(p));
    }

    // ===== Mapping =====

    private PatientProfileResponse toProfileResponse(Patient p) {
        PatientProfileResponse.EmergencyContact ec = null;
        if (p.getEmergencyContactName() != null || p.getEmergencyContactPhone() != null) {
            ec = new PatientProfileResponse.EmergencyContact(
                    p.getEmergencyContactName(),
                    p.getEmergencyContactRelationship(),
                    p.getEmergencyContactPhone());
        }
        return new PatientProfileResponse(
                p.getPatientNumber() != null ? p.getPatientNumber() : String.valueOf(p.getId()),
                p.getFirstName(),
                p.getLastName(),
                p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : null,
                p.getGender() != null ? p.getGender().name().toLowerCase() : "other",
                p.getEmail(),
                p.getPhone(),
                p.getGhanaCard(),
                p.getAddress(),
                ec);
    }

    private MedicalProfileResponse toMedicalResponse(Patient p) {
        List<MedicalProfileResponse.Allergy> allergies = new ArrayList<>();
        for (JsonNode n : parseArray(p.getAllergiesJson())) {
            allergies.add(new MedicalProfileResponse.Allergy(
                    text(n, "id"), text(n, "label"), text(n, "type")));
        }
        List<MedicalProfileResponse.Condition> conditions = new ArrayList<>();
        for (JsonNode n : parseArray(p.getConditionsJson())) {
            conditions.add(new MedicalProfileResponse.Condition(text(n, "id"), text(n, "label")));
        }
        List<MedicalProfileResponse.Medication> medications = new ArrayList<>();
        for (JsonNode n : parseArray(p.getMedicationsJson())) {
            medications.add(new MedicalProfileResponse.Medication(
                    text(n, "id"), text(n, "name"), text(n, "dose")));
        }
        List<MedicalProfileResponse.VitalsEntry> vitals = new ArrayList<>();
        for (JsonNode n : parseArray(p.getVitalsJson())) {
            vitals.add(new MedicalProfileResponse.VitalsEntry(
                    text(n, "id"), text(n, "date"),
                    text(n, "systolic"), text(n, "diastolic"), text(n, "pulseBpm"),
                    text(n, "temperatureC"), text(n, "heightCm"), text(n, "weightKg")));
        }
        return new MedicalProfileResponse(p.getBloodType(), allergies, conditions, medications, vitals);
    }

    // ===== Helpers =====

    private Patient requirePatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
    }

    private static com.example.demo.model.Gender mapGender(String g) {
        return switch (g.toLowerCase()) {
            case "female" -> com.example.demo.model.Gender.FEMALE;
            case "male" -> com.example.demo.model.Gender.MALE;
            default -> com.example.demo.model.Gender.OTHER;
        };
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static ArrayNode parseArray(String raw) {
        if (raw == null || raw.isBlank()) return MAPPER.createArrayNode();
        try {
            JsonNode n = MAPPER.readTree(raw);
            return n != null && n.isArray() ? (ArrayNode) n : MAPPER.createArrayNode();
        } catch (Exception e) {
            return MAPPER.createArrayNode();
        }
    }
}
