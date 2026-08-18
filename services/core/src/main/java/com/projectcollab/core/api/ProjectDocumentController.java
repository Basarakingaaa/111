package com.projectcollab.core.api;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.*;
import com.projectcollab.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/project-documents")
public class ProjectDocumentController {
    private final ProjectDocumentRepository documents;
    private final UserAccountRepository users;
    private final CurrentUserService currentUsers;
    private final AuthorizationService authorization;
    private final ProjectDocumentService storage;
    private final AuditService audit;

    public ProjectDocumentController(ProjectDocumentRepository documents, UserAccountRepository users,
        CurrentUserService currentUsers, AuthorizationService authorization, ProjectDocumentService storage,
        AuditService audit) {
        this.documents = documents; this.users = users; this.currentUsers = currentUsers;
        this.authorization = authorization; this.storage = storage; this.audit = audit;
    }

    @GetMapping
    List<ProjectDocumentView> list(@RequestParam UUID projectId, Authentication authentication) {
        authorization.requireProjectMember(currentUsers.require(authentication), projectId);
        return documents.findByProjectIdOrderByUpdatedAtDesc(projectId).stream().map(this::view).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    ProjectDocumentView upload(@RequestParam UUID projectId, @RequestPart("file") MultipartFile file,
        @RequestParam(required=false) String displayName, @RequestParam(required=false) String description,
        Authentication authentication, HttpServletRequest http) throws IOException {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireWorkEditor(actor, projectId);
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("document").trim();
        if (originalName.isBlank()) originalName = "document";
        if (originalName.length() > 255) throw new IllegalArgumentException("文件名不能超过255个字符");
        String title = displayName == null || displayName.isBlank() ? originalName : displayName.trim();
        if (title.length() > 255) throw new IllegalArgumentException("文档名称不能超过255个字符");
        ProjectDocumentService.StoredFile stored = storage.store(file);
        ProjectDocument document = documents.save(new ProjectDocument(projectId, originalName, title,
            blankToNull(description), file.getContentType(), stored.sizeBytes(), stored.storageKey(),
            stored.sha256(), actor.id));
        audit.record(actor.id, "PROJECT_DOCUMENT_UPLOADED", "PROJECT_DOCUMENT", document.id.toString(), "SUCCESS",
            "project=" + projectId + ",sha256=" + document.sha256, http);
        return view(document);
    }

    @GetMapping("/{id}/download")
    ResponseEntity<FileSystemResource> download(@PathVariable UUID id, Authentication authentication,
                                                 HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        ProjectDocument document = documents.findById(id).orElseThrow();
        authorization.requireProjectMember(actor, document.projectId);
        Path path = storage.path(document);
        audit.record(actor.id, "PROJECT_DOCUMENT_DOWNLOADED", "PROJECT_DOCUMENT", document.id.toString(), "SUCCESS",
            "project=" + document.projectId, http);
        MediaType contentType;
        try { contentType = MediaType.parseMediaType(Optional.ofNullable(document.contentType).orElse("application/octet-stream")); }
        catch (InvalidMediaTypeException ignored) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(document.sizeBytes)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(document.originalName, java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .body(new FileSystemResource(path));
    }

    private ProjectDocumentView view(ProjectDocument document) {
        UserAccount uploader = users.findById(document.uploadedBy).orElse(null);
        return new ProjectDocumentView(document.id, document.projectId, document.displayName, document.originalName,
            document.description, document.contentType, document.sizeBytes, document.sha256,
            document.uploadedBy, uploader == null ? null : uploader.loginName(), document.createdAt.toString(),
            "/api/project-documents/" + document.id + "/download");
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record ProjectDocumentView(UUID id, UUID projectId, String displayName, String originalName,
        String description, String contentType, long sizeBytes, String sha256, UUID uploadedBy,
        String uploaderLogin, String createdAt, String downloadUrl) {}
}
