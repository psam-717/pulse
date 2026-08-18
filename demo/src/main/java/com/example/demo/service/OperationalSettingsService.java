package com.example.demo.service;

import com.example.demo.dto.settings.OperationalSettingsResponse;
import com.example.demo.dto.settings.UpdateOperationalInput;
import com.example.demo.model.OperationalSettings;
import com.example.demo.repository.OperationalSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Settings → Operational (BACKEND_SPEC.md §5.8 / §6.7 rows 6-7).
 *
 * One OperationalSettings row per facility, lazily created with the
 * frontend mock's defaults on first read. queuePriorityLevels is persisted
 * as a pipe-delimited string "id:label:weight|..." (no Jackson dependency)
 * and validated against the known ids emergency | urgent | routine, which
 * mirror the QueuePriority enum weights (3/2/1).
 */
@Service
public class OperationalSettingsService {

    private static final String DEFAULT_LEVELS = "emergency:Emergency:3|urgent:Urgent:2|routine:Routine:1";
    private static final Set<String> KNOWN_IDS = Set.of("emergency", "urgent", "routine");

    private final OperationalSettingsRepository repository;

    public OperationalSettingsService(OperationalSettingsRepository repository) {
        this.repository = repository;
    }

    public OperationalSettingsResponse get(Long facilityId) {
        return toResponse(getOrCreate(facilityId));
    }

    @Transactional
    public OperationalSettingsResponse update(Long facilityId, UpdateOperationalInput input) {
        OperationalSettings s = getOrCreate(facilityId);

        if (input.queueRefreshSeconds() != null) {
            if (input.queueRefreshSeconds() < 3) {
                throw new IllegalArgumentException("queueRefreshSeconds must be at least 3.");
            }
            s.setQueueRefreshSeconds(input.queueRefreshSeconds());
        }
        if (input.appointmentSlotMinutes() != null) {
            if (input.appointmentSlotMinutes() < 5) {
                throw new IllegalArgumentException("appointmentSlotMinutes must be at least 5.");
            }
            s.setAppointmentSlotMinutes(input.appointmentSlotMinutes());
        }
        if (input.noShowGraceMinutes() != null) {
            if (input.noShowGraceMinutes() < 0) {
                throw new IllegalArgumentException("noShowGraceMinutes must not be negative.");
            }
            s.setNoShowGraceMinutes(input.noShowGraceMinutes());
        }
        if (input.queuePriorityLevels() != null) {
            s.setQueuePriorityLevelsJson(serializeLevels(input.queuePriorityLevels()));
        }

        UpdateOperationalInput.NotificationDefaultsInput nd = input.notificationDefaults();
        if (nd != null) {
            if (nd.sendPatientEmailConfirmations() != null) {
                s.setSendPatientEmailConfirmations(nd.sendPatientEmailConfirmations());
            }
            if (nd.sendPatientSmsReminders() != null) {
                s.setSendPatientSmsReminders(nd.sendPatientSmsReminders());
            }
        }

        repository.save(s);
        return toResponse(s);
    }

    // ---- persistence helpers ----

    private OperationalSettings getOrCreate(Long facilityId) {
        return repository.findByFacilityId(facilityId).orElseGet(() -> {
            OperationalSettings s = new OperationalSettings(facilityId);
            s.setQueuePriorityLevelsJson(DEFAULT_LEVELS);
            return repository.save(s);
        });
    }

    private OperationalSettingsResponse toResponse(OperationalSettings s) {
        return new OperationalSettingsResponse(
                parseLevels(s.getQueuePriorityLevelsJson()),
                s.getQueueRefreshSeconds(),
                s.getAppointmentSlotMinutes(),
                s.getNoShowGraceMinutes(),
                new OperationalSettingsResponse.NotificationDefaultsDto(
                        s.isSendPatientEmailConfirmations(),
                        s.isSendPatientSmsReminders()));
    }

    private List<OperationalSettingsResponse.QueuePriorityLevelDto> parseLevels(String stored) {
        List<OperationalSettingsResponse.QueuePriorityLevelDto> levels = new ArrayList<>();
        if (stored == null || stored.isBlank()) {
            // Fall back to enum defaults if the row predates the column.
            for (String entry : DEFAULT_LEVELS.split("\\|")) {
                levels.add(parseEntry(entry));
            }
            return levels;
        }
        for (String entry : stored.split("\\|")) {
            if (entry.isBlank()) continue;
            levels.add(parseEntry(entry));
        }
        return levels;
    }

    private OperationalSettingsResponse.QueuePriorityLevelDto parseEntry(String entry) {
        String[] parts = entry.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalStateException("Corrupt queuePriorityLevels storage: " + entry);
        }
        return new OperationalSettingsResponse.QueuePriorityLevelDto(
                parts[0], parts[1], Integer.parseInt(parts[2]));
    }

    private String serializeLevels(List<UpdateOperationalInput.QueuePriorityLevelDto> levels) {
        StringBuilder sb = new StringBuilder();
        for (UpdateOperationalInput.QueuePriorityLevelDto l : levels) {
            if (!KNOWN_IDS.contains(l.id())) {
                throw new IllegalArgumentException(
                        "Unknown priority id '" + l.id() + "'. Must be one of: emergency, urgent, routine.");
            }
            String label = l.label() == null ? l.id() : l.label().replace(":", "").replace("|", "");
            if (sb.length() > 0) sb.append('|');
            sb.append(l.id()).append(':').append(label).append(':').append(l.weight());
        }
        return sb.toString();
    }
}
