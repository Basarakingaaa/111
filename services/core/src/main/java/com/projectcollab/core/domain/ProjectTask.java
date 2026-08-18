package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="project_task")
public class ProjectTask {
    @Id public UUID id;
    @Column(name="project_id", nullable=false) public UUID projectId;
    @Column(nullable=false) public String title;
    public String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public TaskStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public TaskPriority priority;
    @Column(name="assignee_id") public UUID assigneeId;
    @Column(name="reporter_id", nullable=false) public UUID reporterId;
    @Column(name="due_date") public LocalDate dueDate;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;

    protected ProjectTask() {}

    public ProjectTask(UUID projectId, String title, String description, TaskStatus status,
                       TaskPriority priority, UUID assigneeId, UUID reporterId, LocalDate dueDate) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.assigneeId = assigneeId;
        this.reporterId = reporterId;
        this.dueDate = dueDate;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
