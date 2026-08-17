# Project Collaboration Platform

An independent, remotely deployed collaboration platform for project documents, tasks, people, code knowledge, environments, deployments, notifications, permissions, and seven coordinated AI agents.

## Hard configuration boundaries

1. Deployment bootstrap values come only from environment variables. Copy `.env.example` to `.env` on the remote server and replace every placeholder.
2. Runtime resources such as project IPs, endpoints, accounts, passwords, GitHub App credentials, Slack credentials, SMTP credentials, and tokens are created through the protected UI. Secret fields are encrypted before persistence and masked in normal responses.
3. Users have both system-level and project-level roles. Every API and agent tool call is authorized and audited.

## Services

- `web`: Vue administration UI.
- `core`: Spring Boot source of truth, authorization, approval, audit, and runtime configuration.
- `agent`: Python service hosting seven logical agents.
- `indexer`: incremental document/code indexing into Elasticsearch.
- PostgreSQL, Elasticsearch, Redis, MinIO, and Caddy.

## Remote deployment

```bash
cp .env.example .env
# Replace every placeholder and pin production image versions.
docker compose config
docker compose up -d
```

Only ports 80 and 443 should be publicly exposed. Restrict SSH at the host firewall. PostgreSQL, Redis, Elasticsearch, and MinIO are attached only to the internal Docker network.

## Bootstrap versus runtime settings

Values needed before the first authenticated UI session must be environment variables: database/bootstrap credentials, encryption key, public domain, GitHub OAuth login, service-to-service tokens, and the initial model provider. Once logged in, project-scoped integrations and operational resources are configured through the UI.

## Security

- Generate `APP_CONFIG_ENCRYPTION_KEY` from exactly 32 random bytes encoded as Base64 and store it outside Git.
- Never rotate that key without a controlled re-encryption migration.
- Use a GitHub App for repository integration; do not use a personal access token.
- Elasticsearch is a rebuildable search projection. PostgreSQL, MinIO, and GitHub remain authoritative.
- Passwords, tokens, and private keys never enter Elasticsearch.

