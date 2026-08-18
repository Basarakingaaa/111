package com.projectcollab.core.api;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.UserNotificationRepository;
import com.projectcollab.core.service.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final UserNotificationRepository notifications;
    private final CurrentUserService currentUsers;

    public NotificationController(UserNotificationRepository notifications, CurrentUserService currentUsers) {
        this.notifications = notifications; this.currentUsers = currentUsers;
    }

    @GetMapping
    NotificationList list(Authentication authentication) {
        UserAccount user = currentUsers.require(authentication);
        return new NotificationList(notifications.countByUserIdAndReadAtIsNull(user.id),
            notifications.findTop100ByUserIdOrderByCreatedAtDesc(user.id).stream().map(NotificationView::from).toList());
    }

    @PutMapping("/{id}/read")
    @Transactional
    NotificationView read(@PathVariable UUID id, Authentication authentication) {
        UserAccount user = currentUsers.require(authentication);
        UserNotification notification = notifications.findById(id).orElseThrow();
        if (!notification.userId.equals(user.id)) throw new AccessDeniedException("Notification owner required");
        if (notification.readAt == null) notification.readAt = Instant.now();
        return NotificationView.from(notifications.save(notification));
    }

    @PutMapping("/read-all")
    @Transactional
    Map<String,Integer> readAll(Authentication authentication) {
        UserAccount user = currentUsers.require(authentication);
        return Map.of("updated", notifications.markAllRead(user.id, Instant.now()));
    }

    public record NotificationList(long unreadCount, List<NotificationView> items) {}
    public record NotificationView(UUID id, UUID projectId, String notificationType, String title, String message,
        String relatedType, UUID relatedId, String deliveryStatus, String deliveryDetail, Instant readAt,
        Instant createdAt) {
        static NotificationView from(UserNotification n) {
            return new NotificationView(n.id, n.projectId, n.notificationType, n.title, n.message, n.relatedType,
                n.relatedId, n.deliveryStatus, n.deliveryDetail, n.readAt, n.createdAt);
        }
    }
}
