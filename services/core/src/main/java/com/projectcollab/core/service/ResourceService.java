package com.projectcollab.core.service;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.ManagedResourceRepository;
import com.projectcollab.core.repo.ManagedResourceRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class ResourceService {
    private final ManagedResourceRepository resources;
    private final ManagedResourceRevisionRepository revisions;
    private final AuthorizationService authorization;
    private final CryptoService crypto;

    public ResourceService(ManagedResourceRepository resources, ManagedResourceRevisionRepository revisions,
                           AuthorizationService authorization, CryptoService crypto) {
        this.resources = resources; this.revisions = revisions; this.authorization = authorization; this.crypto = crypto;
    }

    public List<ManagedResource> list(UserAccount user, UUID projectId) {
        if (projectId == null) {
            if (!authorization.isSystemAdmin(user)) throw new org.springframework.security.access.AccessDeniedException("System resources require administrator permission");
            return resources.findByProjectIdIsNullOrderByUpdatedAtDesc();
        }
        authorization.requireProjectMember(user, projectId);
        return resources.findByProjectIdOrderByUpdatedAtDesc(projectId);
    }

    @Transactional
    public ManagedResource create(UserAccount user, ResourceInput input) {
        if (input.projectId() == null) {
            if (!authorization.isSystemAdmin(user)) throw new org.springframework.security.access.AccessDeniedException("System resource requires administrator permission");
        } else authorization.requireResourceManager(user, input.projectId());
        ManagedResource resource = new ManagedResource(user.id);
        apply(resource, user, input);
        ManagedResource saved = resources.saveAndFlush(resource);
        revisions.save(new ManagedResourceRevision(saved, user.id));
        return saved;
    }

    @Transactional
    public ManagedResource update(UserAccount user, UUID id, ResourceInput input) {
        ManagedResource resource = resources.findById(id).orElseThrow();
        if (resource.projectId == null) {
            if (!authorization.isSystemAdmin(user)) throw new org.springframework.security.access.AccessDeniedException("System resource requires administrator permission");
        } else authorization.requireResourceManager(user, resource.projectId);
        if (!Objects.equals(resource.projectId, input.projectId())) throw new IllegalArgumentException("Resource project cannot be changed");
        apply(resource, user, input);
        ManagedResource saved = resources.saveAndFlush(resource);
        revisions.save(new ManagedResourceRevision(saved, user.id));
        return saved;
    }

    public List<ManagedResourceRevision> history(UserAccount user, UUID id) {
        ManagedResource resource = resources.findById(id).orElseThrow();
        if (resource.projectId == null) {
            if (!authorization.isSystemAdmin(user)) throw new org.springframework.security.access.AccessDeniedException("System resource requires administrator permission");
        } else authorization.requireProjectMember(user, resource.projectId);
        return revisions.findByResourceIdOrderByResourceVersionDesc(id);
    }

    public RevealedSecrets reveal(UserAccount user, UUID id) {
        ManagedResource resource = resources.findById(id).orElseThrow();
        authorization.requireSecretReader(user, resource.projectId);
        return new RevealedSecrets(crypto.decrypt(resource.accountCiphertext), crypto.decrypt(resource.secretCiphertext), crypto.decrypt(resource.tokenCiphertext));
    }

    private void apply(ManagedResource r, UserAccount actor, ResourceInput i) {
        r.projectId = i.projectId(); r.name = i.name(); r.resourceType = i.resourceType();
        r.environment = i.environment(); r.endpoint = i.endpoint(); r.host = i.host(); r.port = i.port(); r.notes = i.notes();
        if (i.account() != null && !i.account().isBlank()) r.accountCiphertext = crypto.encrypt(i.account());
        if (i.secret() != null && !i.secret().isBlank()) r.secretCiphertext = crypto.encrypt(i.secret());
        if (i.token() != null && !i.token().isBlank()) r.tokenCiphertext = crypto.encrypt(i.token());
        r.updatedBy = actor.id; r.updatedAt = Instant.now();
    }

    public record ResourceInput(UUID projectId, String name, ResourceType resourceType, String environment,
                                String endpoint, String host, Integer port, String account,
                                String secret, String token, String notes) {}
    public record RevealedSecrets(String account, String secret, String token) {}
}
