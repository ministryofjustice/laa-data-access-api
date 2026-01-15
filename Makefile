.PHONY: localstack-up init-local-resources localstack-down install-awslocal localstack-down-preserve localstack-down-clean prove-persistence

SHELL := /usr/bin/env bash

ENDPOINT := http://localhost:4566
BUCKET := app-history-payloads
TABLE := EventIndexTable
REGION := eu-west-2

# AWS command wrapper: use awslocal if available, otherwise aws with dummy creds
define aws_cmd
	$(if $(shell command -v awslocal 2>/dev/null), \
		AWS_REGION=$(REGION) AWS_DEFAULT_REGION=$(REGION) AWS_PAGER="" awslocal $(1), \
		AWS_REGION=$(REGION) AWS_DEFAULT_REGION=$(REGION) AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_PAGER="" aws --endpoint-url=$(ENDPOINT) --region $(REGION) $(1))
endef

localstack-up:
	@docker compose -f docker-compose.yml up -d --remove-orphans localstack postgres

init-local-resources: localstack-up
	@echo "Checking LocalStack health at $(ENDPOINT)/health ..."
	@for i in 1 2 3 4 5 6 7 8 9 10 11 12; do \
		curl -sS $(ENDPOINT)/health >/dev/null 2>&1 && echo "LocalStack is available" && break; \
		echo "Waiting for LocalStack... ($$i/12)"; \
		sleep 2; \
		if [ $$i -eq 12 ]; then echo "LocalStack did not respond" >&2; exit 3; fi; \
	done
	@echo "Ensuring S3 bucket '$(BUCKET)' exists..."
	@$(call aws_cmd,s3api head-bucket --bucket $(BUCKET)) >/dev/null 2>&1 \
		&& echo "Bucket $(BUCKET) already exists" \
		|| (echo "Creating bucket $(BUCKET)" && \
			($(call aws_cmd,s3api create-bucket --bucket $(BUCKET) --create-bucket-configuration LocationConstraint=$(REGION)) >/dev/null 2>&1 \
			|| $(call aws_cmd,s3api create-bucket --bucket $(BUCKET)) >/dev/null 2>&1 || true) && \
			echo "Bucket created")
	@echo "Ensuring DynamoDB table '$(TABLE)' exists..."
	@$(call aws_cmd,dynamodb describe-table --table-name $(TABLE)) >/dev/null 2>&1 \
		&& echo "Table $(TABLE) already exists" \
		|| (echo "Creating DynamoDB table $(TABLE) (pk/sk)" && \
			$(call aws_cmd,dynamodb create-table \
				--table-name $(TABLE) \
				--attribute-definitions AttributeName=pk$(,)AttributeType=S AttributeName=sk$(,)AttributeType=S \
				--key-schema AttributeName=pk$(,)KeyType=HASH AttributeName=sk$(,)KeyType=RANGE \
				--provisioned-throughput ReadCapacityUnits=5$(,)WriteCapacityUnits=5) >/dev/null && \
			echo "Table created")
	@echo "Done. LocalStack resources ready."

# Install awslocal (awscli-local) using pipx
install-awslocal:
	@python3 -m pip install --user pipx || true
	@python3 -m pipx ensurepath || true
	@pipx install awscli-local || true

localstack-down:
	@docker compose -f docker-compose.yml down -v

localstack-down-preserve:
	@docker compose -f docker-compose.yml down

localstack-down-clean:
	@docker compose -f docker-compose.yml down -v --rmi local

# Prove persistence: create sample data, snapshot volume, restart stack (preserve volumes), and verify
prove-persistence: localstack-up
	@echo "=== Creating sample DynamoDB item and S3 object ==="
	@docker exec laa-localstack awslocal dynamodb put-item --table-name $(TABLE) --item '{"pk":{"S":"test#make"},"sk":{"S":"meta"},"value":{"S":"persist-makefile"}}' >/dev/null 2>&1 || true
	@echo "hello localstack" | docker exec -i laa-localstack awslocal s3 cp - s3://$(BUCKET)/persist-test.txt >/dev/null 2>&1 || true
	@echo "=== Locating LocalStack state volume ==="
	@VOLUME=$$(docker volume ls --format '{{.Name}}' | grep -i localstack | head -n1); \
	if [ -z "$$VOLUME" ]; then echo "No localstack volume found" >&2; exit 2; fi; \
	echo "Using volume: $$VOLUME"; \
	echo "=== Snapshotting volume files (before) ==="; \
	docker run --rm -v $$VOLUME:/vol alpine sh -c "find /vol -type f -exec stat -c '%n %s %Y' {} \; | sort" > /tmp/vol-before.txt; \
	echo "Saved /tmp/vol-before.txt"; \
	echo "=== Restarting compose stack (preserving volumes) ==="; \
	docker compose -f docker-compose.yml down && docker compose -f docker-compose.yml up -d; \
	echo "Waiting for LocalStack to settle..."; sleep 4; \
	echo "=== Verifying resources after restart ==="; \
	docker exec laa-localstack awslocal dynamodb get-item --table-name $(TABLE) --key '{"pk":{"S":"test#make"},"sk":{"S":"meta"}}' >/tmp/get-item-after.json 2>/dev/null || true; \
	docker exec laa-localstack awslocal s3api head-object --bucket $(BUCKET) --key persist-test.txt >/tmp/head-after.txt 2>/dev/null || true; \
	echo "=== Snapshotting volume files (after) ==="; \
	docker run --rm -v $$VOLUME:/vol alpine sh -c "find /vol -type f -exec stat -c '%n %s %Y' {} \; | sort" > /tmp/vol-after.txt; \
	echo "Saved /tmp/vol-after.txt"; \
	DIFF=$$(diff /tmp/vol-before.txt /tmp/vol-after.txt || true); \
	if [ -s /tmp/get-item-after.json ] || [ -s /tmp/head-after.txt ]; then \
		echo "PERSISTENCE OK: resources are present after restart"; \
	else \
		echo "PERSISTENCE FAIL: resources missing" >&2; cat /tmp/get-item-after.json || true; cat /tmp/head-after.txt || true; exit 3; \
	fi; \
	echo "=== Volume diff (before vs after) ==="; echo "$$DIFF" || true

# Escape comma for use inside $(call ...)
, := ,
