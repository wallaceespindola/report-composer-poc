@echo off
REM Stop the Report Composer POC.
REM Usage: scripts\stop.bat [compose|k8s]
setlocal

set TARGET=%1
if "%TARGET%"=="" set TARGET=compose

cd /d "%~dp0.."

if "%TARGET%"=="compose" goto :compose
if "%TARGET%"=="k8s" goto :k8s

echo Unknown target "%TARGET%". Use "compose" or "k8s".
exit /b 1

:compose
echo Stopping Report Composer (docker compose) ...
docker compose down -v
exit /b 0

:k8s
echo Stopping any background port-forward ...
taskkill /FI "WINDOWTITLE eq report-composer-portforward*" /F >NUL 2>&1

echo Deleting Report Composer k8s resources ...
kubectl delete -f k8s/ --ignore-not-found

echo Note: the local cluster (minikube/kind) itself was left running.
echo   Tear it down with "minikube delete" or "kind delete cluster --name report-composer" if desired.
exit /b 0
