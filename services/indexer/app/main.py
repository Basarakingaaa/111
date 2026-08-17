from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from .chunking import markdown_chunks
import os

app = FastAPI(title="Project Collaboration Indexing Service", version="0.1.0", docs_url=None, redoc_url=None)
TOKEN = os.environ["CORE_INDEXER_TOKEN"]

def authorize(value: str | None):
    if value != TOKEN: raise HTTPException(status_code=401, detail="Valid Core Service token required")

class PreviewRequest(BaseModel):
    document_id: str
    content: str
    max_chars: int = Field(default=2400, ge=400, le=8000)

@app.get("/health")
def health(): return {"status":"ok", "mode":"incremental"}

@app.post("/internal/chunks/preview")
def preview(request: PreviewRequest, x_service_token: str | None = Header(default=None)):
    authorize(x_service_token)
    chunks = markdown_chunks(request.document_id, request.content, request.max_chars)
    return [{"chunk_id":c.chunk_id,"section_path":c.section_path,"order":c.order,"content_hash":c.content_hash,"content":c.content} for c in chunks]

