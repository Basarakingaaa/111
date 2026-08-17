# Remote deployment

## Host baseline

- Ubuntu Server 24.04 LTS
- 16 CPU cores, 64 GB RAM, 1 TB NVMe SSD recommended
- Docker Engine with Compose plugin
- DNS record for `PUBLIC_DOMAIN`
- Off-host backup destination

## Prepare

1. Create a GitHub OAuth application. Set its callback URL to `https://<domain>/login/oauth2/code/github`.
2. Copy `.env.example` to `.env` on the server.
3. Generate unique passwords and service tokens. Generate the encryption key with `openssl rand -base64 32`.
4. Set `APP_BOOTSTRAP_ADMIN_GITHUB_LOGIN` to the exact initial administrator login.
5. Validate without printing secrets: `python3 deploy/check_env.py .env`.
6. Run `docker compose config --quiet`.
7. Start with `docker compose up -d`.

## First login

The configured bootstrap GitHub user becomes `SUPER_ADMIN`. Every other first-time GitHub user is persisted as `PENDING` and sees only the waiting-for-approval page. A system administrator activates the account and assigns a system level. A project owner then assigns a project role.

## Runtime resources

After login, use **资源配置** to create servers, databases, APIs, GitHub Apps, Slack Apps, SMTP accounts, model providers, storage, or CI/CD resources. Account, password/secret, and token fields are encrypted. The list API exposes only presence flags. Authorized reveal operations are audited.

## Backups

- PostgreSQL: daily logical backup plus WAL strategy when production usage begins.
- MinIO: versioning and replication to an off-host S3-compatible target.
- Elasticsearch: daily snapshot to off-host storage; it must also remain rebuildable.
- `.env`: encrypted administrative backup separate from application data.

