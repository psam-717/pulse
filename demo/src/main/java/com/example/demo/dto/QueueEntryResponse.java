package com.example.demo.dto;

import com.example.demo.model.QueueEntry;

/** Web dashboard QueueEntry shape (BACKEND_SPEC.md §5.4). */
public record QueueEntryResponse(
        String id,
        String ticketNumber,
        String patientName,
        String departmentId,
        String status,
        String priority,
        String source,
        String checkInAt,
        String calledAt,
        String clinician,
        String room
) {
    public static QueueEntryResponse from(QueueEntry e) {
        return new QueueEntryResponse(
                String.valueOf(e.getId()),
                e.getTicketNumber(),
                e.getPatientName(),
                e.getDepartmentId(),
                e.getStatus().name().toLowerCase(),
                e.getPriority().name().toLowerCase(),
                e.getSource().name().toLowerCase(),
                String.valueOf(e.getCheckInAt()),
                e.getCalledAt() != null ? String.valueOf(e.getCalledAt()) : null,
                e.getClinician(),
                e.getRoom());
    }
}
