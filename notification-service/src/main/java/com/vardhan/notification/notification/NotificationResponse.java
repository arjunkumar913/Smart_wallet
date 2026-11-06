package com.vardhan.notification.notification;

import java.time.Instant;

public record NotificationResponse(Long id, String message, boolean read, Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getMessage(), n.getReadAt() != null, n.getCreatedAt());
    }
}
