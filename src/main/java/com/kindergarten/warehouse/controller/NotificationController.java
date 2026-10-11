package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.NotificationResponse;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        Long userId = viewerResolver.resolve(authentication).userId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getMine(userId, PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)))),
                messageService.getMessage("notification.list.success")));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(Authentication authentication) {
        Long userId = viewerResolver.resolve(authentication).userId();
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", notificationService.countUnread(userId)),
                messageService.getMessage("notification.list.success")));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markRead(id, viewerResolver.resolve(authentication).userId());
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("notification.read.success")));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(Authentication authentication) {
        notificationService.markAllRead(viewerResolver.resolve(authentication).userId());
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("notification.read.success")));
    }
}
