#!/usr/bin/env bash
# Stop the Report Composer POC.
#
# Usage: scripts/stop.sh [compose|k8s]
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

TARGET="${1:-compose}"

stop_compose() {
  echo "Stopping Report Composer (docker compose) ..."
  docker compose down -v
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
