package com.example.demo.service;

import com.example.demo.dto.MedicalRecordsResponse;
import com.example.demo.dto.MedicalRecordsResponse.LabResult;
import com.example.demo.dto.MedicalRecordsResponse.LabValue;
import com.example.demo.dto.MedicalRecordsResponse.Prescription;
import com.example.demo.dto.MedicalRecordsResponse.Visit;
import com.example.demo.model.LabResultRecord;
import com.example.demo.model.PrescriptionRecord;
import com.example.demo.model.VisitRecord;
import com.example.demo.repository.LabResultRecordRepository;
import com.example.demo.repository.PrescriptionRecordRepository;
import com.example.demo.repository.VisitRecordRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class PatientRecordsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VisitRecordRepository visitRecordRepository;
    private final LabResultRecordRepository labResultRecordRepository;
    private final PrescriptionRecordRepository prescriptionRecordRepository;

    public PatientRecordsService(VisitRecordRepository visitRecordRepository,
                                 LabResultRecordRepository labResultRecordRepository,
                                 PrescriptionRecordRepository prescriptionRecordRepository) {
        this.visitRecordRepository = visitRecordRepository;
        this.labResultRecordRepository = labResultRecordRepository;
        this.prescriptionRecordRepository = prescriptionRecordRepository;
    }

    public MedicalRecordsResponse getRecords(Long patientId) {
        List<Visit> visits = visitRecordRepository.findByPatientIdOrderByVisitDateDesc(patientId)
                .stream()
                .map(this::toVisit)
                .toList();
        List<LabResult> labs = labResultRecordRepository.findByPatientIdOrderByResultDateDesc(patientId)
                .stream()
                .map(this::toLab)
                .toList();
        List<Prescription> rxs = prescriptionRecordRepository.findByPatientIdOrderByPrescribedDateDesc(patientId)
                .stream()
                .map(this::toRx)
                .toList();
        return new MedicalRecordsResponse(visits, labs, rxs);
    }

    private Visit toVisit(VisitRecord v) {
        return new Visit(
                v.getPublicId(),
                v.getDepartment(),
                v.getHospital(),
                v.getVisitDate().toString(),
                v.getDoctor(),
                v.getSummary());
    }

    private LabResult toLab(LabResultRecord r) {
        return new LabResult(
                r.getPublicId(),
                r.getTestName(),
                r.getHospital(),
                r.getOrderingDoctor(),
                r.getResultDate().toString(),
                parseValues(r.getValuesJson()));
    }

    private Prescription toRx(PrescriptionRecord p) {
        return new Prescription(
                p.getPublicId(),
                p.getMedication(),
                p.getDose(),
                p.getPrescribingDoctor(),
                p.getHospital(),
                p.getPrescribedDate().toString());
    }

    private List<LabValue> parseValues(String json) {
        List<LabValue> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JsonNode arr = MAPPER.readTree(json);
            if (!arr.isArray()) return out;
            for (JsonNode n : arr) {
                out.add(new LabValue(
                        text(n, "name"),
                        text(n, "value"),
                        text(n, "unit"),
                        text(n, "referenceRange")));
            }
        } catch (Exception e) {
            // leave empty rather than fail the whole records payload
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
