# Single entrypoint for humans and agents. Run `make help` for the target list.
.DEFAULT_GOAL := help
SHELL := /bin/bash

MVNW := ./mvnw

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

.PHONY: verify
verify: ## Full build + tests + quality gates
	$(MVNW) -B verify

.PHONY: test
test: ## Fast unit tests only
	$(MVNW) -B -DskipITs test

.PHONY: fmt
fmt: ## Apply formatting (Spotless for Java)
	$(MVNW) -q spotless:apply

.PHONY: new-adr
new-adr: ## Scaffold a new ADR: make new-adr title="Some decision"
	@scripts/new-adr.sh "$(title)"

.PHONY: release-dry-run
release-dry-run: ## Preview the next semantic-release version and changelog
	npx --no-install semantic-release --dry-run
