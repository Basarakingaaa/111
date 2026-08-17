package com.projectcollab.core.api;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceService resources;
    private final CurrentUserService currentUsers;
    private final AuditService audit;
    public ResourceController(ResourceService resources, CurrentUserService currentUsers, AuditService audit) {
        this.resources = resources; this.currentUsers = currentUsers; this.audit = audit;
    }

    @GetMapping
    List<ResourceView> list(@RequestParam(required=false) UUID projectId, Authentication auth) {
        return resources.list(currentUsers.require(auth), projectId).stream().map(ResourceView::from).toList();
    }

    @PostMapping
    ResourceView create(@Valid @RequestBody ResourceRequest request, Authentication auth, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(auth);
        ManagedResource saved = resources.create(actor, request.toInput());
        audit.record(actor.id, "RESOURCE_CREATED", "MANAGED_RESOURCE", saved.id.toString(), "SUCCESS",
            saved.resourceType.name(), http);
        return ResourceView.from(saved);
    }

    @PutMapping("/{id}")
    ResourceView update(@PathVariable UUID id, @Valid @RequestBody ResourceRequest request,
                        Authentication auth, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(auth);
        ManagedResource saved = resources.update(actor, id, request.toInput());
        audit.record(actor.id, "RESOURCE_UPDATED", "MANAGED_RESOURCE", saved.id.toString(), "SUCCESS",
            saved.resourceType.name(), http);
        return ResourceView.from(saved);
    }

    @PostMapping("/{id}/reveal")
    ResourceService.RevealedSecrets reveal(@PathVariable UUID id, Authentication auth, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(auth);
        ResourceService.RevealedSecrets revealed = resources.reveal(actor, id);
        audit.record(actor.id, "RESOURCE_SECRET_REVEALED", "MANAGED_RESOURCE", id.toString(), "SUCCESS", null, http);
        return revealed;
    }

    public record ResourceRequest(UUID projectId, @NotBlank String name, @NotNull ResourceType resourceType,
        String environment, @Size(max=2048) String endpoint, @Size(max=512) String host,
        @Min(1) @Max(65535) Integer port, String account, String secret, String token, String notes) {
        ResourceService.ResourceInput toInput() { return new ResourceService.ResourceInput(projectId, name, resourceType,
            environment, endpoint, host, port, account, secret, token, notes); }
    }

    public record ResourceView(UUID id, UUID projectId, String name, ResourceType resourceType,
        String environment, String endpoint, String host, Integer port, boolean hasAccount,
        boolean hasSecret, boolean hasToken, String notes, Instant updatedAt, long version) {
        static ResourceView from(ManagedResource r) { return new ResourceView(r.id, r.projectId, r.name, r.resourceType,
            r.environment, r.endpoint, r.host, r.port, r.accountCiphertext != null,
            r.secretCiphertext != null, r.tokenCiphertext != null, r.notes, r.updatedAt, r.version); }
    }
}

