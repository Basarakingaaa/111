# Remote deployment

## Host baseline

- Ubuntu Server 24.04 LTS
- 8 CPU cores, 32 GB RAM, and 200 GB SSD for a pilot deployment; 16 CPU cores, 64 GB RAM, and 1 TB NVMe SSD recommended for heavier production use
- Docker Engine with Compose plugin
- A DNS record for `PUBLIC_DOMAIN` when using `PUBLIC_SCHEME=https`; an IP address is sufficient for an initial `http` deployment
- Set `SESSION_COOKIE_SECURE=false` only for an initial HTTP deployment; restore it to `true` with HTTPS
- `PYPI_INDEX_URL` controls the Python package source used during image builds; use a trusted nearby mirror when the default index is slow
- Allow inbound TCP 80 for an initial HTTP deployment and TCP 443 for HTTPS; restrict SSH port 22 to trusted operator addresses
- Off-host backup destination

## Prepare

1. Create a GitHub OAuth application. Set its callback URL to `<scheme>://<host>/login/oauth2/code/github`, matching `PUBLIC_SCHEME` and `PUBLIC_DOMAIN` exactly.
2. Copy `.env.example` to `.env` on the server.
3. Generate unique passwords and service tokens. Generate the encryption key with `openssl rand -base64 32`.
4. Set `APP_BOOTSTRAP_ADMIN_GITHUB_LOGIN` to the exact initial administrator login.
5. Validate without printing secrets: `python3 deploy/check_env.py .env`.
6. Deploy with `bash deploy/server-deploy.sh`. It validates configuration, builds the four application images, creates the MinIO bucket, and starts the stack.

## Deploy from a Windows operator workstation

The workstation needs OpenSSH (`ssh` and `scp`). Set every connection input through process-level or Windows user-level environment variables; use `deploy/remote.env.example` as the list of required values. The populated application environment file must remain outside the repository.

```powershell
$env:DEPLOY_HOST = "server.example.com"
$env:DEPLOY_PORT = "22"
$env:DEPLOY_USER = "ubuntu"
$env:DEPLOY_SSH_KEY_FILE = "C:\secure\server_ed25519"
$env:DEPLOY_PATH = "/home/ubuntu/project-collaboration"
$env:DEPLOY_REPOSITORY = "https://github.com/Basarakingaaa/111.git"
$env:DEPLOY_BRANCH = "main"
$env:DEPLOY_APP_ENV_FILE = "C:\secure\project-collaboration.env"

powershell -ExecutionPolicy Bypass -File deploy/remote-deploy.ps1
```

The remote user must already be able to run Docker and create `DEPLOY_PATH`. The script never puts SSH credentials or application secrets in Git.

## First login

The configured bootstrap GitHub user becomes `SUPER_ADMIN`. Every other first-time GitHub user is persisted as `PENDING` and sees only the waiting-for-approval page. A system administrator activates the account and assigns a system level. A project owner then assigns a project role.

## Runtime resources

After login, use **资源配置** to create servers, databases, APIs, GitHub Apps, Slack Apps, SMTP accounts, model providers, storage, or CI/CD resources. Account, password/secret, and token fields are encrypted. The list API exposes only presence flags. Authorized reveal operations are audited.

## Backups

- PostgreSQL: daily logical backup plus WAL strategy when production usage begins.
- MinIO: versioning and replication to an off-host S3-compatible target.
- Elasticsearch: daily snapshot to off-host storage; it must also remain rebuildable.
- `.env`: encrypted administrative backup separate from application data.
