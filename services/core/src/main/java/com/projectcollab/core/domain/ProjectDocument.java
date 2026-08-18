package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="project_document")
public class ProjectDocument {
    @Id public UUID id;
    @Column(name="project_id", nullable=false) public UUID projectId;
    @Column(name="original_name", nullable=false) public String originalName;
    @Column(name="display_name", nullable=false) public String displayName;
    public String description;
    @Column(name="content_type") public String contentType;
    @Column(name="size_bytes", nullable=false) public long sizeBytes;
    @Column(name="storage_key", nullable=false, unique=true) public String storageKey;
    @Column(nullable=false) public String sha256;
    @Column(name="uploaded_by", nullable=false) public UUID uploadedBy;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;

    protected ProjectDocument() {}

    public ProjectDocument(UUID projectId, String originalName, String displayName, String description,
                           String contentType, long sizeBytes, String storageKey, String sha256, UUID uploadedBy) {
        this.id = UUID.randomUUID(); this.projectId = projectId; this.originalName = originalName;
        this.displayName = displayName; this.description = description; this.contentType = contentType;
        this.sizeBytes = sizeBytes; this.storageKey = storageKey; this.sha256 = sha256;
        this.uploadedBy = uploadedBy; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
}
