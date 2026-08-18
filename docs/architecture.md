# Architecture

## Deployment units

The MVP has four application services and five infrastructure services:

| Unit | Responsibility |
|---|---|
| Web Console | Browser UI; never receives infrastructure database credentials |
| Core Service | Authoritative business state, GitHub and local-account login, authorization, approval, runtime resources, and audit |
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
- `PENDING`: registered but awaiting approval.

Authentication identities:

- `GITHUB`: OAuth identity tied to an immutable GitHub numeric ID.
- `LOCAL`: administrator-created username with a BCrypt password hash.
- Authentication type does not grant access by itself; system role, active status, and project membership remain authoritative.

Project roles:

- `OWNER`, `MANAGER`, `DEVELOPER`, `TESTER`, `OPERATIONS`, `VIEWER`.
- System administrators create projects, appoint the single primary project manager, and add, update, or remove project members.
- A user may manage multiple projects; each project has one primary project manager.
- The primary project manager creates and manages multiple tasks and assigns each task to one responsible member.
- A task responsible member updates only that task's progress and may create multiple material requests linked to the task.
- Each material request is assigned to one material provider, such as a business analyst providing requirement analysis to development. The provider updates only delivery status and delivery notes.
- Owners, managers, and operations users manage runtime resources and reveal their secrets.
- Every endpoint performs server-side authorization; hiding a button is not treated as security.

## Configuration boundary

Bootstrap configuration is supplied exclusively through environment variables because the UI cannot exist before the platform starts. Runtime project resources are entered through UI and stored as metadata plus AES-256-GCM ciphertext. The encryption key is environment-only and must not be stored in PostgreSQL.

## Agent boundary

The Agent Service contains coordinator, document, task-progress, project-knowledge, environment-deployment, notification, and permission-audit agents. Users never select one manually: the coordinator scores the request intent and routes it to the appropriate specialist. Core Service supplies an authorization-filtered, point-in-time business snapshot and evidence for the selected project, including task and material counts and status distribution. This allows factual questions such as “how many tasks does this project have?” to be answered from PostgreSQL state instead of model memory. Every agent has a tool allowlist. Agents initially produce read steps or approval-required drafts; they never directly commit a high-impact operation.

## Notification boundary

Task and document-request creation, reassignment, status changes, submission, and completion emit persisted per-user notifications. In-app delivery is authoritative and cannot be disabled. Slack and email are best-effort secondary channels configured as encrypted runtime resources; external delivery failure is recorded on the notification and never rolls back the task or document transaction.
