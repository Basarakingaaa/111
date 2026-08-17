#!/usr/bin/env bash
set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${DEPLOY_ENV_FILE:-${project_dir}/.env}"

if [[ ! -f "${env_file}" ]]; then
  echo "ERROR: deployment environment file not found: ${env_file}" >&2
  exit 2
fi

cd "${project_dir}"
python3 deploy/check_env.py "${env_file}"
docker compose --env-file "${env_file}" config --quiet
docker compose --env-file "${env_file}" build --pull web core agent indexer
docker compose --env-file "${env_file}" up -d --remove-orphans
docker compose --env-file "${env_file}" ps
