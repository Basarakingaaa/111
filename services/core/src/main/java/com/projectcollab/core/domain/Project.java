package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="project")
public class Project {
    @Id public UUID id;
    @Column(nullable=false) public String name;
    @Column(nullable=false, unique=true) public String code;
    public String description;
    @Column(name="manager_id") public UUID managerId;
    @Column(name="created_by", nullable=false) public UUID createdBy;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    public boolean archived;

    protected Project() {}
    public Project(String name, String code, String description, UUID createdBy) {
        this.id = UUID.randomUUID(); this.name = name; this.code = code;
        this.description = description; this.createdBy = createdBy;
        this.createdAt = Instant.now(); this.archived = false;
    }
}
