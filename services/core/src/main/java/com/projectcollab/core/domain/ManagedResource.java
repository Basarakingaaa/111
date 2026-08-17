package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="managed_resource")
public class ManagedResource {
    @Id public UUID id;
    @Column(name="project_id") public UUID projectId;
    @Column(nullable=false) public String name;
    @Enumerated(EnumType.STRING) @Column(name="resource_type", nullable=false) public ResourceType resourceType;
    public String environment;
    public String endpoint;
    public String host;
    public Integer port;
    @Column(name="account_ciphertext") public String accountCiphertext;
    @Column(name="secret_ciphertext") public String secretCiphertext;
    @Column(name="token_ciphertext") public String tokenCiphertext;
    public String notes;
    @Column(name="created_by", nullable=false) public UUID createdBy;
    @Column(name="updated_by", nullable=false) public UUID updatedBy;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;
    @Version public long version;

    protected ManagedResource() {}
    public ManagedResource(UUID actorId) {
        this.id = UUID.randomUUID(); this.createdBy = actorId; this.updatedBy = actorId;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
}

