@echo off
REM Start the Report Composer POC.
REM Usage: scripts\start.bat [compose|k8s]
setlocal enabledelayedexpansion

set TARGET=%1
if "%TARGET%"=="" set TARGET=compose

cd /d "%~dp0.."

if "%TARGET%"=="compose" goto :compose
if "%TARGET%"=="k8s" goto :k8s

echo Unknown target "%TARGET%". Use "compose" or "k8s".
exit /b 1

:compose
set WORKER_REPLICAS=%WORKER_REPLICAS%
if "%WORKER_REPLICAS%"=="" set WORKER_REPLICAS=3
echo Starting Report Composer (docker compose, worker replicas=%WORKER_REPLICAS%) ...
docker compose up -d --build --scale worker=%WORKER_REPLICAS%
if errorlevel 1 exit /b 1

echo Waiting for http://localhost:8080/health ...
set /a COUNT=0
:waitloop
curl -sf -o NUL http://localhost:8080/health
if not errorlevel 1 goto :ready
set /a COUNT+=3
if %COUNT% GEQ 180 (
  echo Timed out waiting for API health.
  exit /b 1
)
timeout /t 3 /nobreak >NUL
goto :waitloop

:ready
echo.
echo Report Composer POC is up:
echo   API:            http://localhost:8080
echo   Swagger UI:     http://localhost:8080/swagger-ui.html
echo   Frontend:       http://localhost:3000
echo   MinIO console:  http://localhost:9001
echo   Kafka UI:       http://localhost:8082
echo   H2 (tcp):       localhost:9093
exit /b 0

:k8s
where minikube >NUL 2>&1
if not errorlevel 1 (
  set CLUSTER_CMD=minikube
  minikube status >NUL 2>&1
  if errorlevel 1 (
    echo Starting minikube cluster ...
    minikube start
  )
  goto :k8s_common
)

where kind >NUL 2>&1
if not errorlevel 1 (
  set CLUSTER_CMD=kind
  kind get clusters | findstr /r . >NUL 2>&1
  if errorlevel 1 (
    echo Creating kind cluster ...
    kind create cluster --name report-composer
  )
  goto :k8s_common
)

echo Neither minikube nor kind found on PATH. Install one and re-run.
exit /b 1

:k8s_common
echo Using %CLUSTER_CMD% for the local Kubernetes cluster.

if "%CLUSTER_CMD%"=="minikube" (
  echo Enabling metrics-server addon ...
  minikube addons enable metrics-server
) else (
  echo Installing metrics-server (kind requires --kubelet-insecure-tls) ...
  kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
  kubectl -n kube-system patch deployment metrics-server --type=json -p="[{\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/args/-\",\"value\":\"--kubelet-insecure-tls\"}]"
)

echo Building image report-composer:latest ...
docker build -t report-composer:latest .
if errorlevel 1 exit /b 1

echo Loading image into %CLUSTER_CMD% ...
if "%CLUSTER_CMD%"=="minikube" (
  minikube image load report-composer:latest
) else (
  kind load docker-image report-composer:latest --name report-composer
)

echo Applying k8s manifests ...
kubectl apply -f k8s/

echo Waiting for api rollout ...
kubectl -n report-composer rollout status deployment/report-composer-api --timeout=180s

echo Starting port-forward (background) ...
start "report-composer-portforward" /min kubectl -n report-composer port-forward svc/report-composer-api 8080:8080

echo Waiting for http://localhost:8080/health ...
set /a COUNT=0
:waitloop2
curl -sf -o NUL http://localhost:8080/health
if not errorlevel 1 goto :ready2
set /a COUNT+=3
if %COUNT% GEQ 60 (
  echo Timed out waiting for API health.
  exit /b 1
)
timeout /t 3 /nobreak >NUL
goto :waitloop2

:ready2
echo.
echo Report Composer POC is up on Kubernetes (%CLUSTER_CMD%):
echo   API (port-forwarded): http://localhost:8080
echo   Swagger UI:            http://localhost:8080/swagger-ui.html
echo   Namespace:             report-composer
exit /b 0
