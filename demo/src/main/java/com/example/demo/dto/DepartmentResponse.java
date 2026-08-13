package com.example.demo.dto;

import com.example.demo.model.Department;

/**
 * Web dashboard Department shape (BACKEND_SPEC.md §5.3). Ids are strings to
 * match the frontend contract. Derived fields (doctorsOnDuty, totalDoctors,
 * waiting, inConsultation, avgWaitMinutes, appointmentsToday) are computed
 * server-side — never client-settable; closed/archived departments report
 * zero live floor activity (§7.4).
 */
public record DepartmentResponse(
        String id,
        String name,
        String code,
        String description,
        String status,
        String headDoctorName,
        int doctorsOnDuty,
        int totalDoctors,
        int rooms,
        int waiting,
        int inConsultation,
        int avgWaitMinutes,
        int appointmentsToday,
        String opensAt,
        String closesAt,
        Boolean twentyFourSeven
) {
    public static DepartmentResponse from(Department d, int doctorsOnDuty, int totalDoctors,
                                          int waiting, int inConsultation, int avgWaitMinutes,
                                          int appointmentsToday) {
        boolean inactive = "closed".equals(d.getStatus()) || "archived".equals(d.getStatus());
        return new DepartmentResponse(
                String.valueOf(d.getId()),
                d.getName(),
                d.getAbbreviation() != null ? d.getAbbreviation() : "",
                d.getDescription(),
                d.getStatus() != null ? d.getStatus() : "active",
                d.getHeadDoctorName() != null ? d.getHeadDoctorName() : "",
                inactive ? 0 : doctorsOnDuty,
                inactive ? 0 : totalDoctors,
                d.getRooms() != null ? d.getRooms() : 0,
                inactive ? 0 : waiting,
                inactive ? 0 : inConsultation,
                inactive ? 0 : avgWaitMinutes,
                inactive ? 0 : appointmentsToday,
                d.getOpensAt() != null ? d.getOpensAt() : "08:00",
                d.getClosesAt() != null ? d.getClosesAt() : "17:00",
                Boolean.TRUE.equals(d.getTwentyFourSeven())
        );
    }
}
