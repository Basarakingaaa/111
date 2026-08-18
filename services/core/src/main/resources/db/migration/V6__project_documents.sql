CREATE TABLE project_document (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    sha256 VARCHAR(64) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_project_document_project ON project_document(project_id, updated_at DESC);
