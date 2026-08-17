CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    github_id BIGINT NOT NULL UNIQUE,
    github_login VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    email VARCHAR(320),
    system_role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    description TEXT,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE project_membership (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    project_role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(project_id, user_id)
);

CREATE TABLE managed_resource (
    id UUID PRIMARY KEY,
    project_id UUID REFERENCES project(id),
    name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    environment VARCHAR(64),
    endpoint VARCHAR(2048),
    host VARCHAR(512),
    port INTEGER,
    account_ciphertext TEXT,
    secret_ciphertext TEXT,
    token_ciphertext TEXT,
    notes TEXT,
    created_by UUID NOT NULL REFERENCES app_user(id),
    updated_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_resource_project ON managed_resource(project_id);
CREATE INDEX idx_resource_type ON managed_resource(resource_type);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES app_user(id),
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    outcome VARCHAR(32) NOT NULL,
    details TEXT,
    source_ip VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_created_at ON audit_event(created_at DESC);
CREATE INDEX idx_audit_actor ON audit_event(actor_id, created_at DESC);

