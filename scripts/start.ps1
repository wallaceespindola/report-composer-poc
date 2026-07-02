# Start the Report Composer POC.
# Usage: scripts\start.ps1 [compose|k8s]
param(
    [string]$Target = "compose"
)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

function Wait-ForHealth {
    param([string]$Url, [int]$TimeoutSec = 120)
    Write-Host "Waiting for $Url ..."
    $elapsed = 0
    while ($true) {
        try {
            $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($resp.StatusCode -eq 200) { Write-Host "  ready."; return }
        } catch { }
        Start-Sleep -Seconds 3
        $elapsed += 3
        if ($elapsed -ge $TimeoutSec) {
            Write-Error "Timed out waiting for $Url"
            exit 1
        }
    }
}

function Start-Compose {
    $workerReplicas = if ($env:WORKER_REPLICAS) { $env:WORKER_REPLICAS } else { "3" }
    Write-Host "Starting Report Composer (docker compose, worker replicas=$workerReplicas) ..."
    docker compose up -d --build --scale "worker=$workerReplicas"
    Wait-ForHealth -Url "http://localhost:8080/health" -TimeoutSec 180

    Write-Host ""
    Write-Host "Report Composer POC is up:"
    Write-Host "  API:            http://localhost:8080"
    Write-Host "  Swagger UI:     http://localhost:8080/swagger-ui.html"
    Write-Host "  Frontend:       http://localhost:3000"
    Write-Host "  MinIO console:  http://localhost:9001"
    Write-Host "  Kafka UI:       http://localhost:8082"
    Write-Host "  H2 (tcp):       localhost:9093"
}

function Start-K8s {
    $clusterCmd = ""
    if (Get-Command minikube -ErrorAction SilentlyContinue) {
        $clusterCmd = "minikube"
        minikube status 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Starting minikube cluster ..."
            minikube start
        }
    } elseif (Get-Command kind -ErrorAction SilentlyContinue) {
        $clusterCmd = "kind"
        $clusters = kind get clusters 2>$null
        if (-not $clusters) {
            Write-Host "Creating kind cluster ..."
            kind create cluster --name report-composer
        }
    } else {
        Write-Error "Neither minikube nor kind found on PATH. Install one and re-run."
        exit 1
    }

    Write-Host "Using $clusterCmd for the local Kubernetes cluster."

    if ($clusterCmd -eq "minikube") {
        Write-Host "Enabling metrics-server addon ..."
        minikube addons enable metrics-server
    } else {
        Write-Host "Installing metrics-server (kind requires --kubelet-insecure-tls) ..."
        kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
        kubectl -n kube-system patch deployment metrics-server --type=json `
            -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
    }

    Write-Host "Building image report-composer:latest ..."
    docker build -t report-composer:latest .

    Write-Host "Loading image into $clusterCmd ..."
    if ($clusterCmd -eq "minikube") {
        minikube image load report-composer:latest
    } else {
        kind load docker-image report-composer:latest --name report-composer
    }

    Write-Host "Applying k8s manifests ..."
    kubectl apply -f k8s/

    Write-Host "Waiting for api rollout ..."
    kubectl -n report-composer rollout status deployment/report-composer-api --timeout=180s

    Write-Host "Starting port-forward (background) ..."
    Start-Process -NoNewWindow kubectl -ArgumentList "-n report-composer port-forward svc/report-composer-api 8080:8080"
    Wait-ForHealth -Url "http://localhost:8080/health" -TimeoutSec 60

    Write-Host ""
    Write-Host "Report Composer POC is up on Kubernetes ($clusterCmd):"
    Write-Host "  API (port-forwarded): http://localhost:8080"
    Write-Host "  Swagger UI:            http://localhost:8080/swagger-ui.html"
    Write-Host "  Namespace:             report-composer"
}

switch ($Target) {
    "compose" { Start-Compose }
    "k8s" { Start-K8s }
    default {
        Write-Error "Unknown target '$Target'. Use 'compose' or 'k8s'."
        exit 1
    }
}
