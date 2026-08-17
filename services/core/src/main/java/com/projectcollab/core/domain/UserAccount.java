package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class UserAccount {
    @Id public UUID id;
    @Column(name="github_id", nullable=false, unique=true) public Long githubId;
    @Column(name="github_login", nullable=false, unique=true) public String githubLogin;
    @Column(name="display_name") public String displayName;
    public String email;
    @Enumerated(EnumType.STRING) @Column(name="system_role", nullable=false) public SystemRole systemRole;
    public boolean active;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="last_login_at") public Instant lastLoginAt;

    protected UserAccount() {}

    public UserAccount(Long githubId, String githubLogin, String displayName, String email, SystemRole role, boolean active) {
        this.id = UUID.randomUUID();
        this.githubId = githubId;
        this.githubLogin = githubLogin;
        this.displayName = displayName;
        this.email = email;
        this.systemRole = role;
        this.active = active;
        this.createdAt = Instant.now();
        this.lastLoginAt = Instant.now();
    }
}

