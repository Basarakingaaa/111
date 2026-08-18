package com.projectcollab.core.api;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.*;
import com.projectcollab.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/agent")
public class AgentGatewayController {
    private final CurrentUserService currentUsers;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final ProjectRepository projects;
    private final ProjectTaskRepository tasks;
    private final DocumentRequestRepository documentRequests;
    private final ProjectMembershipRepository memberships;
    private final ManagedResourceRepository resources;
    private final ProjectDocumentRepository projectDocuments;
    private final ProjectDocumentService documentStorage;
    private final RestClient agent;
    private final String token;

    public AgentGatewayController(CurrentUserService currentUsers, AuthorizationService authorization,
        AuditService audit, ProjectRepository projects, ProjectTaskRepository tasks,
        DocumentRequestRepository documentRequests, ProjectMembershipRepository memberships,
        ManagedResourceRepository resources, ProjectDocumentRepository projectDocuments,
        ProjectDocumentService documentStorage, RestClient.Builder builder, @Value("${app.agent-url}") String agentUrl,
        @Value("${app.agent-token}") String token) {
        this.currentUsers = currentUsers; this.authorization = authorization; this.audit = audit;
        this.projects = projects; this.tasks = tasks; this.documentRequests = documentRequests;
        this.memberships = memberships; this.resources = resources;
        this.projectDocuments = projectDocuments; this.documentStorage = documentStorage;
        this.agent = builder.requestFactory(new SimpleClientHttpRequestFactory()).baseUrl(agentUrl).build();
        this.token = token;
    }

    @PostMapping("/runs")
    Map<?,?> run(@Valid @RequestBody RunRequest request, Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireProjectMember(actor, request.projectId());
        UUID runId = UUID.randomUUID();
        Project project = projects.findById(request.projectId()).orElseThrow();
        List<ProjectTask> projectTasks = tasks.findByProjectIdOrderByUpdatedAtDesc(request.projectId());
        List<DocumentRequest> projectDocuments = documentRequests.findByProjectIdOrderByUpdatedAtDesc(request.projectId());
        List<ProjectDocument> storedDocuments = this.projectDocuments.findByProjectIdOrderByUpdatedAtDesc(request.projectId());
        Map<String, Long> taskStatuses = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) taskStatuses.put(status.name(), 0L);
        projectTasks.forEach(task -> taskStatuses.compute(task.status.name(), (key, count) -> count == null ? 1 : count + 1));
        Map<String, Long> documentStatuses = new LinkedHashMap<>();
        for (DocumentRequestStatus status : DocumentRequestStatus.values()) documentStatuses.put(status.name(), 0L);
        projectDocuments.forEach(item -> documentStatuses.compute(item.status.name(), (key, count) -> count == null ? 1 : count + 1));

        Map<String,Object> context = new LinkedHashMap<>();
        context.put("actor_role", actor.systemRole.name());
        context.put("project", Map.of("id", project.id.toString(), "code", project.code, "name", project.name));
        context.put("task_stats", Map.of(
            "total", projectTasks.size(),
            "assigned", projectTasks.stream().filter(task -> task.assigneeId != null).count(),
            "unassigned", projectTasks.stream().filter(task -> task.assigneeId == null).count(),
            "by_status", taskStatuses));
        context.put("tasks", projectTasks.stream().limit(100).map(task -> {
            Map<String,Object> fact = new LinkedHashMap<>();
            fact.put("id", task.id.toString()); fact.put("title", task.title);
            fact.put("status", task.status.name()); fact.put("priority", task.priority.name());
            if (task.assigneeId != null) fact.put("assignee_id", task.assigneeId.toString());
            if (task.dueDate != null) fact.put("due_date", task.dueDate.toString());
            return fact;
        }).toList());
        context.put("document_stats", Map.of("total", projectDocuments.size(), "by_status", documentStatuses));
        context.put("project_document_stats", Map.of("total", storedDocuments.size()));
        context.put("project_documents", storedDocuments.stream().limit(50).map(document -> {
            Map<String,Object> fact = new LinkedHashMap<>();
            fact.put("id", document.id.toString()); fact.put("display_name", document.displayName);
            fact.put("original_name", document.originalName); fact.put("size_bytes", document.sizeBytes);
            fact.put("content_type", Optional.ofNullable(document.contentType).orElse("application/octet-stream"));
            fact.put("download_url", "/api/project-documents/" + document.id + "/download");
            if (document.description != null) fact.put("description", document.description);
            String preview = documentStorage.textPreview(document, 2000);
            if (preview != null && !preview.isBlank()) fact.put("text_excerpt", preview);
            return fact;
        }).toList());
        context.put("member_count", memberships.findByProjectId(project.id).size());
        context.put("resource_count", resources.findByProjectIdOrderByUpdatedAtDesc(project.id).size());

        String observedAt = Instant.now().toString();
        List<Map<String,Object>> evidence = List.of(
            Map.of("source_type", "project_task_database", "source_id", project.id.toString(),
                "version", observedAt, "observed_at", observedAt,
                "excerpt", "任务总数=" + projectTasks.size() + "，状态分布=" + taskStatuses),
            Map.of("source_type", "document_request_database", "source_id", project.id.toString(),
                "version", observedAt, "observed_at", observedAt,
                "excerpt", "材料需求总数=" + projectDocuments.size() + "，状态分布=" + documentStatuses),
            Map.of("source_type", "project_document_database", "source_id", project.id.toString(),
                "version", observedAt, "observed_at", observedAt,
                "excerpt", "项目文档总数=" + storedDocuments.size()));
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("run_id", runId.toString()); payload.put("actor_id", actor.id.toString());
        payload.put("project_id", request.projectId().toString()); payload.put("message", request.message());
        payload.put("context", context); payload.put("evidence", evidence);
        Map<?,?> result = agent.post().uri("/runs").contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", token).body(payload).retrieve().body(Map.class);
        audit.record(actor.id, "AGENT_RUN", "AGENT_RUN", runId.toString(), "SUCCESS", "automatic-routing", http);
        return result == null ? Map.of("run_id", runId, "status", "empty") : result;
    }

    public record RunRequest(@NotNull UUID projectId, @NotBlank @Size(max=20000) String message) {}
}
