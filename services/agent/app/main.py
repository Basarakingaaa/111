from fastapi import FastAPI, Header, HTTPException
from .agents import AGENTS, Coordinator
from .config import settings
from .models import AgentRunRequest, AgentResult

app = FastAPI(title="Project Collaboration Agent Service", version="0.1.0", docs_url=None, redoc_url=None)

def authorize(x_service_token: str | None) -> None:
    if not x_service_token or x_service_token != settings.CORE_AGENT_TOKEN:
        raise HTTPException(status_code=401, detail="Valid Core Service token required")

@app.get("/health")
def health(): return {"status":"ok", "agents":len(AGENTS)}

@app.get("/agents")
def list_agents(x_service_token: str | None = Header(default=None)):
    authorize(x_service_token)
    return [{"name": a.name, "title": a.title, "mission": a.mission, "allowed_tools": a.allowed_tools} for a in AGENTS.values()]

@app.post("/runs", response_model=AgentResult)
def run(request: AgentRunRequest, x_service_token: str | None = Header(default=None)):
    authorize(x_service_token)
    agent = Coordinator.select(request)
    return agent.run(request)

