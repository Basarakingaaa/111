$ErrorActionPreference = 'Stop'

$required = @(
    'DEPLOY_HOST',
    'DEPLOY_USER',
    'DEPLOY_PATH',
    'DEPLOY_REPOSITORY',
    'DEPLOY_APP_ENV_FILE'
)

foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Required environment variable is missing: $name"
    }
}

$deployHost = $env:DEPLOY_HOST
$deployUser = $env:DEPLOY_USER
$deployPath = $env:DEPLOY_PATH
$repository = $env:DEPLOY_REPOSITORY
$branch = if ($env:DEPLOY_BRANCH) { $env:DEPLOY_BRANCH } else { 'main' }
$port = if ($env:DEPLOY_PORT) { [int]$env:DEPLOY_PORT } else { 22 }
$appEnvFile = (Resolve-Path -LiteralPath $env:DEPLOY_APP_ENV_FILE).Path

if ($deployHost -notmatch '^[A-Za-z0-9.-]+$') { throw 'DEPLOY_HOST is invalid' }
if ($deployUser -notmatch '^[A-Za-z0-9._-]+$') { throw 'DEPLOY_USER is invalid' }
if ($deployPath -notmatch '^/[A-Za-z0-9._/-]+$') { throw 'DEPLOY_PATH must be an absolute Linux path' }
if ($branch -notmatch '^[A-Za-z0-9._/-]+$') { throw 'DEPLOY_BRANCH is invalid' }
if ($repository -notmatch '^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\.git)?$') {
    throw 'DEPLOY_REPOSITORY must be an HTTPS GitHub repository URL'
}
if ($port -lt 1 -or $port -gt 65535) { throw 'DEPLOY_PORT is invalid' }

$sshArgs = @('-p', "$port", '-o', 'StrictHostKeyChecking=accept-new')
$scpArgs = @('-P', "$port", '-o', 'StrictHostKeyChecking=accept-new')
if ($env:DEPLOY_SSH_KEY_FILE) {
    $keyFile = (Resolve-Path -LiteralPath $env:DEPLOY_SSH_KEY_FILE).Path
    $sshArgs += @('-i', $keyFile)
    $scpArgs += @('-i', $keyFile)
}

$target = "${deployUser}@${deployHost}"
$prepare = @"
set -Eeuo pipefail
if [ -d '${deployPath}/.git' ]; then
  git -C '${deployPath}' fetch origin '${branch}'
  git -C '${deployPath}' checkout '${branch}'
  git -C '${deployPath}' pull --ff-only origin '${branch}'
else
  mkdir -p '${deployPath}'
  git clone --branch '${branch}' '${repository}' '${deployPath}'
fi
"@

$prepare | & ssh @sshArgs $target 'bash -s'
if ($LASTEXITCODE -ne 0) { throw 'Remote repository preparation failed' }

& scp @scpArgs $appEnvFile "${target}:${deployPath}/.env"
if ($LASTEXITCODE -ne 0) { throw 'Environment upload failed' }

$deploy = "chmod 600 '${deployPath}/.env' && cd '${deployPath}' && bash deploy/server-deploy.sh"
& ssh @sshArgs $target $deploy
if ($LASTEXITCODE -ne 0) { throw 'Remote deployment failed' }
