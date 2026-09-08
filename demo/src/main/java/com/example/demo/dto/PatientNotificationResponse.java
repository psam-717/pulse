package com.example.demo.dto;

import com.example.demo.model.PatientNotification;

/** Patient in-app notification shape (mobile feed). */
public record PatientNotificationResponse(
        String id,
        String type,
        String title,
        String body,
        String createdAt,
        boolean read,
        String link
) {
    public static PatientNotificationResponse from(PatientNotification n) {
        return new PatientNotificationResponse(
                String.valueOf(n.getId()),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                String.valueOf(n.getCreatedAt()),
                n.isRead(),
                n.getLink());
    }
}
