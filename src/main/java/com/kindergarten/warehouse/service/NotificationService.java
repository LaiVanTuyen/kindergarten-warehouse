package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.NotificationResponse;
import com.kindergarten.warehouse.entity.Notification;
import com.kindergarten.warehouse.entity.NotificationType;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications (bell icon) for uploaders and reporters. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Joins the caller's transaction so the notification commits together with
     * the status change that caused it.
     */
    @Transactional
    public void notify(Long userId, NotificationType type, String title, String message, String link) {
        if (userId == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(truncate(title, 255))
                .message(truncate(message, 1000))
                .link(truncate(link, 500))
                .build());
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMine(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.setIsRead(true);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
