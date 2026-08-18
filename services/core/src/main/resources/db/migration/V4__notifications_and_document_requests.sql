CREATE TABLE document_request (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    assignee_id UUID REFERENCES app_user(id),
    requester_id UUID NOT NULL REFERENCES app_user(id),
    due_date DATE,
    delivery_note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_document_request_project ON document_request(project_id, updated_at DESC);
CREATE INDEX idx_document_request_assignee ON document_request(assignee_id, status);

CREATE TABLE user_notification (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    project_id UUID REFERENCES project(id),
    notification_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    related_type VARCHAR(64),
    related_id UUID,
    delivery_status VARCHAR(32) NOT NULL,
    delivery_detail TEXT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_notification_user ON user_notification(user_id, created_at DESC);
CREATE INDEX idx_user_notification_unread ON user_notification(user_id, read_at, created_at DESC);
