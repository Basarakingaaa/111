from typing import Any, Literal
from pydantic import BaseModel, Field

AgentName = Literal[
    "coordinator", "document", "task-progress", "project-knowledge",
    "environment-deployment", "notification", "permission-audit"
]

class Evidence(BaseModel):
    source_type: str
    source_id: str
    version: str | None = None
    observed_at: str | None = None
    excerpt: str | None = None

class AgentRunRequest(BaseModel):
    run_id: str
    actor_id: str
    project_id: str
    message: str = Field(min_length=1, max_length=20000)
    requested_agent: AgentName | None = None
    context: dict[str, Any] = Field(default_factory=dict)
    evidence: list[Evidence] = Field(default_factory=list)

class ProposedAction(BaseModel):
    tool: str
    reason: str
    parameters: dict[str, Any] = Field(default_factory=dict)
    risk: Literal["read", "low", "high"] = "read"
    requires_approval: bool = False

class AgentResult(BaseModel):
    run_id: str
    agent: AgentName
    summary: str
    findings: list[str] = Field(default_factory=list)
    proposed_actions: list[ProposedAction] = Field(default_factory=list)
    evidence: list[Evidence] = Field(default_factory=list)
    assumptions: list[str] = Field(default_factory=list)
    needs_human_input: bool = False

