package com.projectcollab.core.service;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class NotificationService {
    private final UserNotificationRepository notifications;
    private final UserAccountRepository users;
    private final NotificationDeliveryService delivery;

    public NotificationService(UserNotificationRepository notifications, UserAccountRepository users,
                               NotificationDeliveryService delivery) {
        this.notifications = notifications; this.users = users; this.delivery = delivery;
    }

    public void send(Collection<UUID> recipientIds, UUID projectId, String type, String title, String message,
                     String relatedType, UUID relatedId) {
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(recipientIds);
        unique.remove(null);
        for (UUID userId : unique) {
            users.findById(userId).filter(u -> u.active).ifPresent(recipient -> {
                UserNotification notification = notifications.save(new UserNotification(userId, projectId, type,
                    title, message, relatedType, relatedId));
                NotificationDeliveryService.DeliveryResult result = delivery.deliver(notification, recipient);
                notification.deliveryStatus = result.status(); notification.deliveryDetail = result.detail();
                notifications.save(notification);
            });
        }
    }
}
