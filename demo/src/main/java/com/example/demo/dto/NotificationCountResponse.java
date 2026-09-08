package com.example.demo.dto;

/** GET /notifications/unread-count → { "count": n }. */
public record NotificationCountResponse(long count) {
}
