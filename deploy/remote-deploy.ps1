$ErrorActionPreference = 'Stop'

function Get-DeployVariable {
    param([Parameter(Mandatory)][string]$Name)
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, 'User')
    }
    return $value
}

$required = @(
    'DEPLOY_HOST',
    'DEPLOY_USER',
    'DEPLOY_PATH',
    'DEPLOY_REPOSITORY',
    'DEPLOY_APP_ENV_FILE'
)

foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace((Get-DeployVariable $name))) {
        throw "Required environment variable is missing: $name"
    }
}

$deployHost = Get-DeployVariable 'DEPLOY_HOST'
$deployUser = Get-DeployVariable 'DEPLOY_USER'
$deployPath = Get-DeployVariable 'DEPLOY_PATH'
$repository = Get-DeployVariable 'DEPLOY_REPOSITORY'
$configuredBranch = Get-DeployVariable 'DEPLOY_BRANCH'
$configuredPort = Get-DeployVariable 'DEPLOY_PORT'
$configuredKeyFile = Get-DeployVariable 'DEPLOY_SSH_KEY_FILE'
$branch = if ($configuredBranch) { $configuredBranch } else { 'main' }
$port = if ($configuredPort) { [int]$configuredPort } else { 22 }
$appEnvFile = (Resolve-Path -LiteralPath (Get-DeployVariable 'DEPLOY_APP_ENV_FILE')).Path

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
if ($configuredKeyFile) {
    $keyFile = (Resolve-Path -LiteralPath $configuredKeyFile).Path
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
