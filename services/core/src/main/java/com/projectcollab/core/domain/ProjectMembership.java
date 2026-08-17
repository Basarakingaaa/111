package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="project_membership")
public class ProjectMembership {
    @Id public UUID id;
    @Column(name="project_id", nullable=false) public UUID projectId;
    @Column(name="user_id", nullable=false) public UUID userId;
    @Enumerated(EnumType.STRING) @Column(name="project_role", nullable=false) public ProjectRole projectRole;
    @Column(name="created_at", nullable=false) public Instant createdAt;

    protected ProjectMembership() {}
    public ProjectMembership(UUID projectId, UUID userId, ProjectRole role) {
        this.id = UUID.randomUUID(); this.projectId = projectId; this.userId = userId;
        this.projectRole = role; this.createdAt = Instant.now();
    }
}

