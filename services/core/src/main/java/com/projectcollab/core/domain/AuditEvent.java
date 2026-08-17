package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="audit_event")
public class AuditEvent {
    @Id public UUID id;
    @Column(name="actor_id") public UUID actorId;
    @Column(nullable=false) public String action;
    @Column(name="resource_type", nullable=false) public String resourceType;
    @Column(name="resource_id") public String resourceId;
    @Column(nullable=false) public String outcome;
    public String details;
    @Column(name="source_ip") public String sourceIp;
    @Column(name="created_at", nullable=false) public Instant createdAt;

    protected AuditEvent() {}
    public AuditEvent(UUID actorId, String action, String resourceType, String resourceId,
                      String outcome, String details, String sourceIp) {
        this.id = UUID.randomUUID(); this.actorId = actorId; this.action = action;
        this.resourceType = resourceType; this.resourceId = resourceId; this.outcome = outcome;
        this.details = details; this.sourceIp = sourceIp; this.createdAt = Instant.now();
    }
}

