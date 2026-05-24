.PHONY: setup dev backend frontend db-up db-down test test-backend test-frontend lint lint-backend lint-frontend format check arch coverage outdated security migrate clean help

# --- Setup ---

setup: ## Install dependencies, start DB, apply migrations
	@echo "==> Installing frontend dependencies"
	cd frontend && pnpm install
	@echo "==> Starting PostgreSQL"
	docker compose up -d postgres
	@echo "==> Waiting for PostgreSQL to be healthy"
	@until docker compose exec -T postgres pg_isready -U photo -d photo_management >/dev/null 2>&1; do sleep 1; done
	@echo "==> Applying Flyway migrations"
	cd backend && ./gradlew flywayMigrate -i 2>/dev/null || echo "  (Flyway runs automatically on app start)"

# --- Dev ---

dev: ## Run backend and frontend in parallel
	@$(MAKE) -j2 backend frontend

backend: ## Run backend (requires Postgres up)
	cd backend && ./gradlew bootRun

frontend: ## Run frontend dev server
	cd frontend && pnpm dev

db-up: ## Start PostgreSQL
	docker compose up -d postgres

db-down: ## Stop PostgreSQL
	docker compose down

# --- Test ---

test: test-backend test-frontend ## Run all tests

test-backend: ## Run backend tests (requires Docker for Testcontainers)
	cd backend && ./gradlew test

test-frontend: ## Run frontend tests
	cd frontend && pnpm test

arch: ## Run ArchUnit architecture tests only
	cd backend && ./gradlew test --tests 'com.example.photomanagement.arch.*'

coverage: ## Generate JaCoCo HTML coverage report at backend/build/reports/jacoco/test/html/
	cd backend && ./gradlew test jacocoTestReport
	@echo "Report: backend/build/reports/jacoco/test/html/index.html"

# --- Lint / Format ---

lint: lint-backend lint-frontend ## Run all linters

lint-backend: ## Run backend linters (Spotless check, Checkstyle, SpotBugs)
	cd backend && ./gradlew spotlessCheck checkstyleMain checkstyleTest spotbugsMain

lint-frontend: ## Run frontend linters
	cd frontend && pnpm lint && pnpm format:check && pnpm typecheck

format: ## Auto-format both backend and frontend
	cd backend && ./gradlew spotlessApply
	cd frontend && pnpm format && pnpm lint:fix

# --- Check (CI parity) ---

check: lint test ## Run everything CI runs (lint + test, includes ArchUnit and JaCoCo)

# --- Security / Dependency hygiene ---

security: ## Run OWASP Dependency Check (slow without NVD_API_KEY)
	cd backend && ./gradlew dependencyCheckAnalyze

outdated: ## Report outdated Gradle dependencies
	cd backend && ./gradlew dependencyUpdates

# --- Misc ---

migrate: ## Apply Flyway migrations manually
	cd backend && ./gradlew flywayMigrate -i

clean: ## Clean build outputs
	cd backend && ./gradlew clean
	cd frontend && rm -rf .next node_modules/.cache

help: ## Show this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
