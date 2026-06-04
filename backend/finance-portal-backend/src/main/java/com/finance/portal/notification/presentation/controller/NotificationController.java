package com.finance.portal.notification.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.presentation.dto.NotificationResponse;
import com.finance.portal.notification.presentation.mapper.NotificationMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<NotificationResponse> body = notificationService.getUserNotifications(userId).stream()
                .map(NotificationMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body, "Notifications retrieved successfully"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(ApiResponse.success(notificationService.countUnread(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(ApiResponse.success(
                NotificationMapper.toResponse(notificationService.markRead(userId, id)), "Marked as read"));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(ApiResponse.success(notificationService.markAllRead(userId), "All marked as read"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        String userId = jwt.getSubject();
        notificationService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Notification deleted"));
    }
}
