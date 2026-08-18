package com.projectcollab.core.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="document_request")
public class DocumentRequest {
    @Id public UUID id;
    @Column(name="project_id", nullable=false) public UUID projectId;
    @Column(name="task_id") public UUID taskId;
    @Column(nullable=false) public String title;
    public String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public DocumentRequestStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public TaskPriority priority;
    @Column(name="assignee_id") public UUID assigneeId;
    @Column(name="requester_id", nullable=false) public UUID requesterId;
    @Column(name="due_date") public LocalDate dueDate;
    @Column(name="delivery_note") public String deliveryNote;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;

    protected DocumentRequest() {}

    public DocumentRequest(UUID projectId, UUID taskId, String title, String description, DocumentRequestStatus status,
                           TaskPriority priority, UUID assigneeId, UUID requesterId, LocalDate dueDate) {
        this.id = UUID.randomUUID(); this.projectId = projectId; this.taskId = taskId; this.title = title;
        this.description = description; this.status = status; this.priority = priority;
        this.assigneeId = assigneeId; this.requesterId = requesterId; this.dueDate = dueDate;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
}
