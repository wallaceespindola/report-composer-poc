#!/usr/bin/env bash
# Stop the Report Composer POC.
#
# Usage: scripts/stop.sh [compose|k8s]
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

TARGET="${1:-compose}"

# Compose may ignore the active docker context (e.g. colima) — pin DOCKER_HOST to it
if [ -z "${DOCKER_HOST:-}" ]; then
  ctx_host="$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
  [ -n "${ctx_host}" ] && export DOCKER_HOST="${ctx_host}"
fi

stop_compose() {
  echo "Stopping Report Composer (docker compose) ..."
  if docker compose version >/dev/null 2>&1; then
    docker compose down -v
  else
    docker-compose down -v
  fi
}

stop_k8s() {
  echo "Stopping any background port-forward ..."
  pkill -f "port-forward svc/report-composer-api" 2>/dev/null || true

  echo "Deleting Report Composer k8s resources ..."
  kubectl delete -f k8s/ --ignore-not-found

  echo "Note: the local cluster (minikube/kind) itself was left running."
  echo "  Tear it down with 'minikube delete' or 'kind delete cluster --name report-composer' if desired."
}

case "${TARGET}" in
  compose) stop_compose ;;
  k8s) stop_k8s ;;
  *)
    echo "Unknown target '${TARGET}'. Use 'compose' or 'k8s'." >&2
    exit 1
    ;;
esac
