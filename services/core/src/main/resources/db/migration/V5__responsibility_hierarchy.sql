ALTER TABLE project ADD COLUMN manager_id UUID REFERENCES app_user(id);

UPDATE project p
SET manager_id = (
    SELECT pm.user_id
    FROM project_membership pm
    JOIN app_user u ON u.id = pm.user_id
    WHERE pm.project_id = p.id
      AND pm.project_role IN ('MANAGER', 'OWNER')
    ORDER BY CASE pm.project_role WHEN 'MANAGER' THEN 0 ELSE 1 END,
             CASE WHEN u.system_role IN ('SUPER_ADMIN', 'SYSTEM_ADMIN') THEN 1 ELSE 0 END,
             pm.created_at
    LIMIT 1
)
WHERE p.manager_id IS NULL;

CREATE INDEX idx_project_manager ON project(manager_id, archived);

ALTER TABLE document_request ADD COLUMN task_id UUID REFERENCES project_task(id) ON DELETE CASCADE;

CREATE INDEX idx_document_request_task ON document_request(task_id, updated_at DESC);
