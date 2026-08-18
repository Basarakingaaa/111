package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="user_notification")
public class UserNotification {
    @Id public UUID id;
    @Column(name="user_id", nullable=false) public UUID userId;
    @Column(name="project_id") public UUID projectId;
    @Column(name="notification_type", nullable=false) public String notificationType;
    @Column(nullable=false) public String title;
    @Column(nullable=false) public String message;
    @Column(name="related_type") public String relatedType;
    @Column(name="related_id") public UUID relatedId;
    @Column(name="delivery_status", nullable=false) public String deliveryStatus;
    @Column(name="delivery_detail") public String deliveryDetail;
    @Column(name="read_at") public Instant readAt;
    @Column(name="created_at", nullable=false) public Instant createdAt;

    protected UserNotification() {}

    public UserNotification(UUID userId, UUID projectId, String type, String title, String message,
                            String relatedType, UUID relatedId) {
        this.id = UUID.randomUUID(); this.userId = userId; this.projectId = projectId;
        this.notificationType = type; this.title = title; this.message = message;
        this.relatedType = relatedType; this.relatedId = relatedId;
        this.deliveryStatus = "IN_APP"; this.createdAt = Instant.now();
    }
}
