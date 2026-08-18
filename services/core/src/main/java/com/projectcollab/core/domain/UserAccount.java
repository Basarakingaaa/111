package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class UserAccount {
    @Id public UUID id;
    @Column(name="github_id", unique=true) public Long githubId;
    @Column(name="github_login", unique=true) public String githubLogin;
    @Column(length=64) public String username;
    @Column(name="password_hash") public String passwordHash;
    @Enumerated(EnumType.STRING) @Column(name="auth_type", nullable=false) public AuthType authType;
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
        this.authType = AuthType.GITHUB;
        this.displayName = displayName;
        this.email = email;
        this.systemRole = role;
        this.active = active;
        this.createdAt = Instant.now();
        this.lastLoginAt = Instant.now();
    }

    public static UserAccount local(String username, String passwordHash, String displayName,
                                    String email, SystemRole role, boolean active) {
        UserAccount user = new UserAccount();
        user.id = UUID.randomUUID();
        user.username = username;
        user.passwordHash = passwordHash;
        user.authType = AuthType.LOCAL;
        user.displayName = displayName;
        user.email = email;
        user.systemRole = role;
        user.active = active;
        user.createdAt = Instant.now();
        return user;
    }

    public String loginName() {
        return authType == AuthType.LOCAL ? username : githubLogin;
    }
}
