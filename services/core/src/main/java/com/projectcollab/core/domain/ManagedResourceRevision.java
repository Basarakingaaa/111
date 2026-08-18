package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="managed_resource_revision")
public class ManagedResourceRevision {
    @Id public UUID id;
    @Column(name="resource_id", nullable=false) public UUID resourceId;
    @Column(name="resource_version", nullable=false) public long resourceVersion;
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
    @Column(name="changed_by", nullable=false) public UUID changedBy;
    @Column(name="changed_at", nullable=false) public Instant changedAt;

    protected ManagedResourceRevision() {}

    public ManagedResourceRevision(ManagedResource resource, UUID changedBy) {
        this.id = UUID.randomUUID(); this.resourceId = resource.id; this.resourceVersion = resource.version;
        this.name = resource.name; this.resourceType = resource.resourceType; this.environment = resource.environment;
        this.endpoint = resource.endpoint; this.host = resource.host; this.port = resource.port;
        this.accountCiphertext = resource.accountCiphertext; this.secretCiphertext = resource.secretCiphertext;
        this.tokenCiphertext = resource.tokenCiphertext; this.notes = resource.notes;
        this.changedBy = changedBy; this.changedAt = Instant.now();
    }
}
