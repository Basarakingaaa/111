CREATE TABLE project_task (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    assignee_id UUID REFERENCES app_user(id),
    reporter_id UUID NOT NULL REFERENCES app_user(id),
    due_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_project_task_project ON project_task(project_id, updated_at DESC);
CREATE INDEX idx_project_task_assignee ON project_task(assignee_id, status);
