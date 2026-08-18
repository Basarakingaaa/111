package com.projectcollab.core.api;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.*;
import com.projectcollab.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final ProjectTaskRepository tasks;
    private final UserAccountRepository users;
    private final ProjectMembershipRepository memberships;
    private final DocumentRequestRepository documentRequests;
    private final CurrentUserService currentUsers;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final NotificationService notifications;

    public TaskController(ProjectTaskRepository tasks, UserAccountRepository users,
                          ProjectMembershipRepository memberships, CurrentUserService currentUsers,
                          DocumentRequestRepository documentRequests, AuthorizationService authorization,
                          AuditService audit, NotificationService notifications) {
        this.tasks = tasks; this.users = users; this.memberships = memberships;
        this.documentRequests = documentRequests;
        this.currentUsers = currentUsers; this.authorization = authorization; this.audit = audit;
        this.notifications = notifications;
    }

    @GetMapping
    List<TaskView> list(@RequestParam UUID projectId, Authentication authentication) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireProjectMember(actor, projectId);
        return tasks.findByProjectIdOrderByUpdatedAtDesc(projectId).stream().map(task -> view(task, actor)).toList();
    }

    @PostMapping
    @Transactional
    TaskView create(@Valid @RequestBody CreateTask request, Authentication authentication,
                    HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireTaskEditor(actor, request.projectId());
        UserAccount assignee = validateAssignee(request.projectId(), request.assigneeId());
        ProjectTask task = tasks.save(new ProjectTask(request.projectId(), request.title().trim(),
            blankToNull(request.description()), request.status() == null ? TaskStatus.TODO : request.status(),
            request.priority() == null ? TaskPriority.MEDIUM : request.priority(),
            assignee == null ? null : assignee.id, actor.id, request.dueDate()));
        audit.record(actor.id, "TASK_CREATED", "TASK", task.id.toString(), "SUCCESS",
            "project=" + task.projectId + ",assignee=" + task.assigneeId, http);
        notifications.send(List.of(task.assigneeId == null ? actor.id : task.assigneeId), task.projectId,
            "TASK_CREATED", "新任务：" + task.title, "任务已创建并等待处理。", "TASK", task.id);
        return view(task, actor);
    }

    @PutMapping("/{taskId}")
    @Transactional
    TaskView update(@PathVariable UUID taskId, @Valid @RequestBody UpdateTask request,
                    Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        ProjectTask task = tasks.findById(taskId).orElseThrow();
        authorization.requireProjectManager(actor, task.projectId);
        TaskStatus previousStatus = task.status;
        UUID previousAssignee = task.assigneeId;
        UserAccount assignee = validateAssignee(task.projectId, request.assigneeId());
        task.title = request.title().trim();
        task.description = blankToNull(request.description());
        task.status = request.status();
        task.priority = request.priority();
        task.assigneeId = assignee == null ? null : assignee.id;
        task.dueDate = request.dueDate();
        task.updatedAt = Instant.now();
        tasks.save(task);
        audit.record(actor.id, "TASK_UPDATED", "TASK", task.id.toString(), "SUCCESS",
            "status=" + task.status + ",assignee=" + task.assigneeId, http);
        if (!Objects.equals(previousAssignee, task.assigneeId) && task.assigneeId != null) {
            notifications.send(List.of(task.assigneeId), task.projectId, "TASK_ASSIGNED", "任务已分配：" + task.title,
                "你已成为该任务负责人。", "TASK", task.id);
        }
        if (previousStatus != task.status) {
            String type = task.status == TaskStatus.DONE ? "TASK_COMPLETED" : "TASK_STATUS_CHANGED";
            String title = task.status == TaskStatus.DONE ? "任务已完成：" + task.title : "任务状态已更新：" + task.title;
            notifications.send(Arrays.asList(task.reporterId, task.assigneeId), task.projectId, type, title,
                "当前状态：" + task.status, "TASK", task.id);
        }
        return view(task, actor);
    }

    @PatchMapping("/{taskId}/progress")
    @Transactional
    TaskView updateProgress(@PathVariable UUID taskId, @Valid @RequestBody ProgressUpdate request,
                            Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        ProjectTask task = tasks.findById(taskId).orElseThrow();
        boolean manager = authorization.isProjectManager(actor, task.projectId);
        if (!manager && !Objects.equals(task.assigneeId, actor.id)) {
            throw new org.springframework.security.access.AccessDeniedException("Only the task assignee can update task progress");
        }
        TaskStatus previousStatus = task.status;
        task.status = request.status();
        task.updatedAt = Instant.now();
        tasks.save(task);
        if (previousStatus != task.status) {
            String type = task.status == TaskStatus.DONE ? "TASK_COMPLETED" : "TASK_STATUS_CHANGED";
            String title = task.status == TaskStatus.DONE ? "任务已完成：" + task.title : "任务状态已更新：" + task.title;
            notifications.send(Arrays.asList(task.reporterId, task.assigneeId), task.projectId, type, title,
                "当前状态：" + task.status, "TASK", task.id);
        }
        audit.record(actor.id, "TASK_PROGRESS_UPDATED", "TASK", task.id.toString(), "SUCCESS",
            "status=" + task.status, http);
        return view(task, actor);
    }

    private UserAccount validateAssignee(UUID projectId, UUID assigneeId) {
        if (assigneeId == null) return null;
        UserAccount assignee = users.findById(assigneeId).orElseThrow();
        if (!assignee.active) throw new IllegalArgumentException("Inactive users cannot own tasks");
        if (memberships.findByProjectIdAndUserId(projectId, assigneeId).isEmpty()) {
            throw new IllegalArgumentException("Task assignee must be a project member");
        }
        return assignee;
    }

    private TaskView view(ProjectTask task, UserAccount actor) {
        UserAccount assignee = task.assigneeId == null ? null : users.findById(task.assigneeId).orElse(null);
        UserAccount reporter = users.findById(task.reporterId).orElse(null);
        return new TaskView(task.id, task.projectId, task.title, task.description, task.status, task.priority,
            task.assigneeId, assignee == null ? null : assignee.loginName(), task.reporterId,
            reporter == null ? null : reporter.loginName(), task.dueDate, task.createdAt, task.updatedAt,
            documentRequests.countByTaskId(task.id), authorization.isProjectManager(actor, task.projectId),
            Objects.equals(task.assigneeId, actor.id) || authorization.isProjectManager(actor, task.projectId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateTask(@NotNull UUID projectId, @NotBlank @Size(max=255) String title,
        String description, TaskStatus status, TaskPriority priority, UUID assigneeId, LocalDate dueDate) {}
    public record UpdateTask(@NotBlank @Size(max=255) String title, String description,
        @NotNull TaskStatus status, @NotNull TaskPriority priority, UUID assigneeId, LocalDate dueDate) {}
    public record ProgressUpdate(@NotNull TaskStatus status) {}
    public record TaskView(UUID id, UUID projectId, String title, String description, TaskStatus status,
        TaskPriority priority, UUID assigneeId, String assigneeLogin, UUID reporterId, String reporterLogin,
        LocalDate dueDate, Instant createdAt, Instant updatedAt, long documentRequestCount,
        boolean canManage, boolean canUpdateProgress) {}
}
