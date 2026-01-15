.PHONY: localstack-up init-local-resources localstack-down install-awslocal

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
	@docker-compose up -d localstack postgres

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
	@docker-compose down -v

# Escape comma for use inside $(call ...)
, := ,
