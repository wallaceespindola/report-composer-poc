# Stop the Report Composer POC.
# Usage: scripts\stop.ps1 [compose|k8s]
param(
    [string]$Target = "compose"
)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

function Stop-Compose {
    Write-Host "Stopping Report Composer (docker compose) ..."
    docker compose down -v
}

function Stop-K8s {
    Write-Host "Stopping any background port-forward ..."
    Get-Process kubectl -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -match "port-forward svc/report-composer-api"
    } | Stop-Process -Force -ErrorAction SilentlyContinue

    Write-Host "Deleting Report Composer k8s resources ..."
    kubectl delete -f k8s/ --ignore-not-found

    Write-Host "Note: the local cluster (minikube/kind) itself was left running."
    Write-Host "  Tear it down with 'minikube delete' or 'kind delete cluster --name report-composer' if desired."
}

switch ($Target) {
    "compose" { Stop-Compose }
    "k8s" { Stop-K8s }
    default {
        Write-Error "Unknown target '$Target'. Use 'compose' or 'k8s'."
        exit 1
    }
}
