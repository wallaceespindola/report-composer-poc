#!/usr/bin/env bash
# Start the Report Composer POC.
#
# Usage: scripts/start.sh [compose|k8s]
#   compose (default) — docker compose up, master + workers in-process/local.
#   k8s               — minikube or kind, full topology incl. HPA + master Job.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

TARGET="${1:-compose}"
WORKER_REPLICAS="${WORKER_REPLICAS:-3}"

wait_for_health() {
  local url="$1" timeout="${2:-120}" elapsed=0
  echo "Waiting for ${url} ..."
  until curl -sf -o /dev/null "${url}"; do
    sleep 3
    elapsed=$((elapsed + 3))
    if [ "${elapsed}" -ge "${timeout}" ]; then
      echo "Timed out waiting for ${url}" >&2
      exit 1
    fi
  done
  echo "  ready."
}

start_compose() {
  echo "Starting Report Composer (docker compose, worker replicas=${WORKER_REPLICAS}) ..."
  docker compose up -d --build --scale "worker=${WORKER_REPLICAS}"
  wait_for_health "http://localhost:8080/health" 180

  cat <<EOF

Report Composer POC is up:
  API:            http://localhost:8080
  Swagger UI:     http://localhost:8080/swagger-ui.html
  Frontend:       http://localhost:3000
  MinIO console:  http://localhost:9001
  Kafka UI:       http://localhost:8082
  H2 (tcp):       localhost:9093
EOF
}

start_k8s() {
  local cluster_cmd=""
  if command -v minikube >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
    cluster_cmd="minikube"
  elif command -v kind >/dev/null 2>&1 && kind get clusters 2>/dev/null | grep -q .; then
    cluster_cmd="kind"
  elif command -v minikube >/dev/null 2>&1; then
    echo "Starting minikube cluster ..."
    minikube start
    cluster_cmd="minikube"
  elif command -v kind >/dev/null 2>&1; then
    echo "Creating kind cluster ..."
    kind create cluster --name report-composer
    cluster_cmd="kind"
  else
    echo "Neither minikube nor kind found on PATH. Install one and re-run." >&2
    exit 1
  fi

  echo "Using ${cluster_cmd} for the local Kubernetes cluster."

  if [ "${cluster_cmd}" = "minikube" ]; then
    echo "Enabling metrics-server addon ..."
    minikube addons enable metrics-server
  else
    echo "Installing metrics-server (kind requires --kubelet-insecure-tls) ..."
    kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
    kubectl -n kube-system patch deployment metrics-server --type=json \
      -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' \
      || echo "  (patch may already be applied)"
  fi

  echo "Building image report-composer:latest ..."
  docker build -t report-composer:latest .

  echo "Loading image into ${cluster_cmd} ..."
  if [ "${cluster_cmd}" = "minikube" ]; then
    minikube image load report-composer:latest
  else
    kind load docker-image report-composer:latest --name report-composer
  fi

  echo "Applying k8s manifests ..."
  kubectl apply -f k8s/

  echo "Waiting for api rollout ..."
  kubectl -n report-composer rollout status deployment/report-composer-api --timeout=180s

  echo "Starting port-forward (background) ..."
  kubectl -n report-composer port-forward svc/report-composer-api 8080:8080 >/tmp/report-composer-portforward.log 2>&1 &
  disown || true
  wait_for_health "http://localhost:8080/health" 60

  cat <<EOF

Report Composer POC is up on Kubernetes (${cluster_cmd}):
  API (port-forwarded): http://localhost:8080
  Swagger UI:            http://localhost:8080/swagger-ui.html
  Namespace:             report-composer
  Watch pods:            kubectl -n report-composer get pods -w
  Watch HPA:              kubectl -n report-composer get hpa -w
EOF
}

case "${TARGET}" in
  compose) start_compose ;;
  k8s) start_k8s ;;
  *)
    echo "Unknown target '${TARGET}'. Use 'compose' or 'k8s'." >&2
    exit 1
    ;;
esac
