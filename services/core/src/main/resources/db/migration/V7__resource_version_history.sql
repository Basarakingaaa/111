CREATE TABLE managed_resource_revision (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES managed_resource(id) ON DELETE CASCADE,
    resource_version BIGINT NOT NULL,
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
    changed_by UUID NOT NULL REFERENCES app_user(id),
    changed_at TIMESTAMPTZ NOT NULL,
    UNIQUE(resource_id, resource_version)
);

CREATE INDEX idx_resource_revision_resource ON managed_resource_revision(resource_id, resource_version DESC);
