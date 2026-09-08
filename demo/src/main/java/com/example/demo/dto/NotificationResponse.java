package com.example.demo.dto;

import com.example.demo.model.Notification;

/** Web notification shape (BACKEND_SPEC.md §5.7 / §6.6). */
public record NotificationResponse(
        String id,
        String type,
        String title,
        String body,
        String createdAt,
        boolean read,
        String link
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                String.valueOf(n.getId()),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                String.valueOf(n.getCreatedAt()),
                n.isRead(),
                n.getLink());
    }
}
