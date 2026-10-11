package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.Notification;
import com.kindergarten.warehouse.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String link,
        boolean isRead,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                Boolean.TRUE.equals(n.getIsRead()), n.getCreatedAt());
    }
}
