package com.projectcollab.core.api;

import com.projectcollab.core.domain.UserAccount;
import com.projectcollab.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.*;

@RestController
@RequestMapping("/api/agent")
public class AgentGatewayController {
    private final CurrentUserService currentUsers;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final RestClient agent;
    private final String token;

    public AgentGatewayController(CurrentUserService currentUsers, AuthorizationService authorization,
        AuditService audit, RestClient.Builder builder, @Value("${app.agent-url}") String agentUrl,
        @Value("${app.agent-token}") String token) {
        this.currentUsers = currentUsers; this.authorization = authorization; this.audit = audit;
        this.agent = builder.baseUrl(agentUrl).build(); this.token = token;
    }

    @PostMapping("/runs")
    Map<?,?> run(@Valid @RequestBody RunRequest request, Authentication authentication, HttpServletRequest http) {
        UserAccount actor = currentUsers.require(authentication);
        authorization.requireProjectMember(actor, request.projectId());
        UUID runId = UUID.randomUUID();
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("run_id", runId.toString()); payload.put("actor_id", actor.id.toString());
        payload.put("project_id", request.projectId().toString()); payload.put("message", request.message());
        if (request.requestedAgent() != null && !request.requestedAgent().isBlank()) payload.put("requested_agent", request.requestedAgent());
        payload.put("context", Map.of("actor_role", actor.systemRole.name())); payload.put("evidence", List.of());
        Map<?,?> result = agent.post().uri("/runs").contentType(MediaType.APPLICATION_JSON)
            .header("X-Service-Token", token).body(payload).retrieve().body(Map.class);
        audit.record(actor.id, "AGENT_RUN", "AGENT_RUN", runId.toString(), "SUCCESS", request.requestedAgent(), http);
        return result == null ? Map.of("run_id", runId, "status", "empty") : result;
    }

    public record RunRequest(@NotNull UUID projectId, @NotBlank @Size(max=20000) String message, String requestedAgent) {}
}

