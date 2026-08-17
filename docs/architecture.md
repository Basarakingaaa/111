# Architecture

## Deployment units

The MVP has four application services and five infrastructure services:

| Unit | Responsibility |
|---|---|
| Web Console | Browser UI; never receives infrastructure database credentials |
| Core Service | Authoritative business state, GitHub login, authorization, approval, runtime resources, and audit |
| Agent Service | Seven logical agents; reachable only by Core Service |
| Indexing Service | Incremental parsing, stable chunking, embeddings, and Elasticsearch projections |
| PostgreSQL | Authoritative transactional data |
| Elasticsearch | Rebuildable full-text/vector search projections |
| Redis | Cache, scheduling, and queues |
| MinIO | Original files and generated artifacts |
| Caddy | Public HTTPS entry point |

## Trust boundaries

- Browsers reach only Caddy, Web Console, and authenticated Core APIs.
- Agent and Indexing services are attached only to the internal network.
- Agents do not query PostgreSQL and cannot decrypt runtime resources.
- A runtime secret reveal requires an authorized user, a dedicated endpoint, and an audit event.
- Elasticsearch never receives passwords, tokens, private keys, or decrypted resource accounts.

## User levels

System roles:

- `SUPER_ADMIN`: bootstrap owner and highest authority.
- `SYSTEM_ADMIN`: user approval and system administration, except super-admin management.
- `STANDARD`: ordinary authenticated user; project access still requires membership.
- `READ_ONLY`: cannot create projects or mutate managed project data.
- `PENDING`: registered by GitHub login but awaiting approval.

Project roles:

- `OWNER`, `MANAGER`, `DEVELOPER`, `TESTER`, `OPERATIONS`, `VIEWER`.
- Project owners and managers assign members.
- Owners, managers, and operations users manage runtime resources and reveal their secrets.
- Every endpoint performs server-side authorization; hiding a button is not treated as security.

## Configuration boundary

Bootstrap configuration is supplied exclusively through environment variables because the UI cannot exist before the platform starts. Runtime project resources are entered through UI and stored as metadata plus AES-256-GCM ciphertext. The encryption key is environment-only and must not be stored in PostgreSQL.

## Agent boundary

The Agent Service contains coordinator, document, task-progress, project-knowledge, environment-deployment, notification, and permission-audit agents. Every agent has a tool allowlist. Agents initially produce read steps or approval-required drafts; they never directly commit a high-impact operation.

