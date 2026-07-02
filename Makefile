.DEFAULT_GOAL := help

.PHONY: up up-k8s down down-k8s test image help

up: ## Start the POC with docker compose (master + workers on one machine)
	scripts/start.sh compose

up-k8s: ## Start the POC on local Kubernetes (minikube/kind)
	scripts/start.sh k8s

down: ## Stop the docker compose stack
	scripts/stop.sh compose

down-k8s: ## Delete the Kubernetes resources
	scripts/stop.sh k8s

test: ## Run the full Maven build with tests
	./mvnw -B verify

image: ## Build the report-composer:latest image
	docker build -t report-composer:latest .

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'
