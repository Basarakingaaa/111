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
@RequestMapping("/api/document-requests")
public class DocumentRequestController {
    private final DocumentRequestRepository requests;
    private final UserAccountRepository users;
    private final ProjectMembershipRepository memberships;
    private final ProjectTaskRepository tasks;
    private final CurrentUserService currentUsers;
    private final AuthorizationService authorization;
    private final NotificationService notifications;
    private final AuditService audit;

    public DocumentRequestController(DocumentRequestRepository requests, UserAccountRepository users,
        ProjectMembershipRepository memberships, ProjectTaskRepository tasks, CurrentUserService currentUsers,
        AuthorizationService authorization, NotificationService notifications, AuditService audit) {
        this.requests = requests; this.users = users; this.memberships = memberships;
        this.tasks = tasks;
        this.currentUsers = currentUsers; this.authorization = authorization;
        this.notifications = notifications; this.audit = audit;
    }

    @GetMapping
    List<DocumentRequestView> list(@RequestParam UUID projectId, Authentication authentication) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireProjectMember(actor, projectId);
        return requests.findByProjectIdOrderByUpdatedAtDesc(projectId).stream().map(item -> view(item, actor)).toList();
    }

    @PostMapping
    @Transactional
    DocumentRequestView create(@Valid @RequestBody CreateDocumentRequest request, Authentication authentication,
                               HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        ProjectTask task = tasks.findById(request.taskId()).orElseThrow();
        if (!Objects.equals(task.projectId, request.projectId())) {
            throw new IllegalArgumentException("材料需求与任务必须属于同一个项目");
        }
        if (!Objects.equals(task.assigneeId, actor.id)) {
            throw new org.springframework.security.access.AccessDeniedException("只有任务负责人可以发起该任务的材料需求");
        }
        UserAccount assignee = validateAssignee(request.projectId(), request.assigneeId());
        DocumentRequest created = requests.save(new DocumentRequest(request.projectId(), request.taskId(), request.title().trim(),
            blankToNull(request.description()), request.status() == null ? DocumentRequestStatus.REQUESTED : request.status(),
            request.priority() == null ? TaskPriority.MEDIUM : request.priority(),
            assignee.id, actor.id, request.dueDate()));
        notifications.send(List.of(created.assigneeId), created.projectId,
            "DOCUMENT_REQUESTED", "新材料需求：" + created.title,
            "任务“" + task.title + "”需要你提供该材料。", "DOCUMENT_REQUEST", created.id);
        audit.record(actor.id, "DOCUMENT_REQUEST_CREATED", "DOCUMENT_REQUEST", created.id.toString(), "SUCCESS",
            "assignee=" + created.assigneeId, http);
        return view(created, actor);
    }

    @PutMapping("/{id}")
    @Transactional
    DocumentRequestView update(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request,
                               Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        DocumentRequest item = requests.findById(id).orElseThrow();
        if (!Objects.equals(item.requesterId, actor.id) && !authorization.isProjectManager(actor, item.projectId)) {
            throw new org.springframework.security.access.AccessDeniedException("只有材料需求发起人或项目管理员可以修改需求");
        }
        UUID previousAssignee = item.assigneeId;
        DocumentRequestStatus previousStatus = item.status;
        UserAccount assignee = validateAssignee(item.projectId, request.assigneeId());
        item.title = request.title().trim(); item.description = blankToNull(request.description());
        item.status = request.status(); item.priority = request.priority();
        item.assigneeId = assignee == null ? null : assignee.id; item.dueDate = request.dueDate();
        item.deliveryNote = blankToNull(request.deliveryNote()); item.updatedAt = Instant.now();
        requests.save(item);

        if (!Objects.equals(previousAssignee, item.assigneeId) && item.assigneeId != null) {
            notifications.send(List.of(item.assigneeId), item.projectId, "DOCUMENT_ASSIGNED", "材料需求已分配：" + item.title,
                "你已成为该材料的负责人。", "DOCUMENT_REQUEST", item.id);
        }
        if (previousStatus != item.status) {
            String type = item.status == DocumentRequestStatus.DONE ? "DOCUMENT_COMPLETED" : "DOCUMENT_STATUS_CHANGED";
            String title = item.status == DocumentRequestStatus.DONE ? "材料需求已完成：" + item.title : "材料状态已更新：" + item.title;
            String message = "当前状态：" + item.status + (item.deliveryNote == null ? "" : "\n交付说明：" + item.deliveryNote);
            notifications.send(Arrays.asList(item.requesterId, item.assigneeId), item.projectId, type, title, message,
                "DOCUMENT_REQUEST", item.id);
        }
        audit.record(actor.id, "DOCUMENT_REQUEST_UPDATED", "DOCUMENT_REQUEST", item.id.toString(), "SUCCESS",
            "status=" + item.status, http);
        return view(item, actor);
    }

    @PatchMapping("/{id}/delivery")
    @Transactional
    DocumentRequestView deliver(@PathVariable UUID id, @Valid @RequestBody DeliveryUpdate request,
                                Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        DocumentRequest item = requests.findById(id).orElseThrow();
        if (!Objects.equals(item.assigneeId, actor.id)) {
            throw new org.springframework.security.access.AccessDeniedException("只有材料提供人可以提交或完成该材料");
        }
        DocumentRequestStatus previousStatus = item.status;
        item.status = request.status();
        item.deliveryNote = blankToNull(request.deliveryNote());
        item.updatedAt = Instant.now();
        requests.save(item);
        if (previousStatus != item.status) {
            String type = item.status == DocumentRequestStatus.DONE ? "DOCUMENT_COMPLETED" : "DOCUMENT_STATUS_CHANGED";
            String title = item.status == DocumentRequestStatus.DONE ? "材料需求已完成：" + item.title : "材料状态已更新：" + item.title;
            notifications.send(List.of(item.requesterId), item.projectId, type, title,
                "当前状态：" + item.status + (item.deliveryNote == null ? "" : "\n交付说明：" + item.deliveryNote),
                "DOCUMENT_REQUEST", item.id);
        }
        audit.record(actor.id, "DOCUMENT_DELIVERY_UPDATED", "DOCUMENT_REQUEST", item.id.toString(), "SUCCESS",
            "status=" + item.status, http);
        return view(item, actor);
    }

    private UserAccount validateAssignee(UUID projectId, UUID assigneeId) {
        if (assigneeId == null) throw new IllegalArgumentException("材料需求必须指定材料提供人");
        UserAccount assignee = users.findById(assigneeId).orElseThrow();
        if (!assignee.active) throw new IllegalArgumentException("Inactive users cannot own document requests");
        if (memberships.findByProjectIdAndUserId(projectId, assigneeId).isEmpty())
            throw new IllegalArgumentException("Document request assignee must be a project member");
        return assignee;
    }

    private DocumentRequestView view(DocumentRequest item, UserAccount actor) {
        UserAccount assignee = item.assigneeId == null ? null : users.findById(item.assigneeId).orElse(null);
        UserAccount requester = users.findById(item.requesterId).orElse(null);
        ProjectTask task = item.taskId == null ? null : tasks.findById(item.taskId).orElse(null);
        return new DocumentRequestView(item.id, item.projectId, item.taskId, task == null ? null : task.title,
            item.title, item.description, item.status, item.priority,
            item.assigneeId, assignee == null ? null : assignee.loginName(), item.requesterId,
            requester == null ? null : requester.loginName(), item.dueDate, item.deliveryNote, item.createdAt, item.updatedAt,
            Objects.equals(item.requesterId, actor.id) || authorization.isProjectManager(actor, item.projectId),
            Objects.equals(item.assigneeId, actor.id));
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record CreateDocumentRequest(@NotNull UUID projectId, @NotNull UUID taskId,
        @NotBlank @Size(max=255) String title, String description, DocumentRequestStatus status,
        TaskPriority priority, @NotNull UUID assigneeId, LocalDate dueDate) {}
    public record UpdateDocumentRequest(@NotBlank @Size(max=255) String title, String description,
        @NotNull DocumentRequestStatus status, @NotNull TaskPriority priority, UUID assigneeId, LocalDate dueDate,
        String deliveryNote) {}
    public record DeliveryUpdate(@NotNull DocumentRequestStatus status, String deliveryNote) {}
    public record DocumentRequestView(UUID id, UUID projectId, UUID taskId, String taskTitle, String title, String description,
        DocumentRequestStatus status, TaskPriority priority, UUID assigneeId, String assigneeLogin,
        UUID requesterId, String requesterLogin, LocalDate dueDate, String deliveryNote, Instant createdAt,
        Instant updatedAt, boolean canManage, boolean canDeliver) {}
}
