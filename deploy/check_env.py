#!/usr/bin/env python3
"""Validate remote deployment variables without printing any secret values."""
from __future__ import annotations
import base64
import pathlib
import re
import sys

REQUIRED = {
    "COMPOSE_PROJECT_NAME", "PUBLIC_DOMAIN", "PUBLIC_SCHEME", "TLS_EMAIL", "HTTP_PORT", "HTTPS_PORT",
    "WEB_IMAGE", "CORE_IMAGE", "AGENT_IMAGE", "INDEXER_IMAGE", "POSTGRES_IMAGE",
    "REDIS_IMAGE", "ELASTICSEARCH_IMAGE", "MINIO_IMAGE", "MINIO_MC_IMAGE", "CADDY_IMAGE",
    "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD",
    "REDIS_PASSWORD", "MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "ELASTIC_PASSWORD",
    "MINIO_BUCKET", "ES_JAVA_OPTS",
    "APP_CONFIG_ENCRYPTION_KEY", "APP_BOOTSTRAP_ADMIN_GITHUB_LOGIN",
    "GITHUB_OAUTH_CLIENT_ID", "GITHUB_OAUTH_CLIENT_SECRET", "MODEL_PROVIDER",
    "MODEL_BASE_URL", "MODEL_API_KEY", "MODEL_NAME", "EMBEDDING_MODEL",
    "CORE_AGENT_TOKEN", "CORE_INDEXER_TOKEN", "APP_LOG_LEVEL", "APP_TIME_ZONE",
    "UPLOAD_MAX_BYTES", "AGENT_MAX_CONTEXT_TOKENS", "INDEXER_WORKERS",
}
SECRET_KEYS = {
    "POSTGRES_PASSWORD", "REDIS_PASSWORD", "MINIO_ROOT_PASSWORD", "ELASTIC_PASSWORD",
    "GITHUB_OAUTH_CLIENT_SECRET", "MODEL_API_KEY", "CORE_AGENT_TOKEN", "CORE_INDEXER_TOKEN",
}

def parse(path: pathlib.Path) -> dict[str,str]:
    values: dict[str,str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line: continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values

def main() -> int:
    path = pathlib.Path(sys.argv[1] if len(sys.argv)>1 else ".env")
    if not path.is_file(): print(f"ERROR: {path} does not exist"); return 2
    values, errors = parse(path), []
    for key in sorted(REQUIRED):
        value = values.get(key, "")
        if not value: errors.append(f"{key} is missing")
        elif "replace-with" in value or "your-org" in value or value.endswith("example.com"):
            errors.append(f"{key} still contains a placeholder")
    try:
        if len(base64.b64decode(values.get("APP_CONFIG_ENCRYPTION_KEY", ""), validate=True)) != 32:
            errors.append("APP_CONFIG_ENCRYPTION_KEY must decode to exactly 32 bytes")
    except Exception: errors.append("APP_CONFIG_ENCRYPTION_KEY is not valid Base64")
    for key in SECRET_KEYS:
        value = values.get(key, "")
        if value and len(value) < 24: errors.append(f"{key} must contain at least 24 characters")
    reused = {}
    for key in SECRET_KEYS:
        value = values.get(key)
        if value: reused.setdefault(value, []).append(key)
    for keys in reused.values():
        if len(keys)>1: errors.append("credentials must be unique: " + ", ".join(sorted(keys)))
    if not re.fullmatch(r"[A-Za-z0-9.-]+", values.get("PUBLIC_DOMAIN", "")):
        errors.append("PUBLIC_DOMAIN is invalid")
    if values.get("PUBLIC_SCHEME") not in {"http", "https"}:
        errors.append("PUBLIC_SCHEME must be http or https")
    for key in ("HTTP_PORT", "HTTPS_PORT", "UPLOAD_MAX_BYTES", "AGENT_MAX_CONTEXT_TOKENS", "INDEXER_WORKERS"):
        try:
            if int(values.get(key, "0")) <= 0: errors.append(f"{key} must be a positive integer")
        except ValueError: errors.append(f"{key} must be a positive integer")
    if errors:
        for error in errors: print("ERROR:", error)
        return 1
    print("Environment validation passed; no secret values were displayed.")
    return 0

if __name__ == "__main__": raise SystemExit(main())
