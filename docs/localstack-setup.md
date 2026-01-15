# Local development with LocalStack (DynamoDB + S3)

This guide explains how to run LocalStack with Docker Compose for local development of S3 and DynamoDB, including the test DynamoDB event controller.

## Prerequisites

- Docker and Docker Compose installed.
- Java 21 (for running the Spring Boot app).
- AWS CLI v2 installed (for testing). Optionally `awslocal` (LocalStack CLI helper) can be used — it avoids needing configured AWS credentials and automatically targets LocalStack.

## Quick start (one command)

```bash
# Install awslocal, start LocalStack + Postgres, create S3 bucket and DynamoDB table
make install-awslocal
make init-local-resources
```

## Install `awslocal` (recommended)

`awslocal` is a wrapper around the AWS CLI that automatically targets LocalStack. It avoids credential prompts.

```bash
# Using pipx (recommended, isolated install)
python3 -m pip install --user pipx
python3 -m pipx ensurepath
# Open a new shell or source your profile if ensurepath asks you to
pipx install awscli-local

# Alternatively via Makefile helper
make install-awslocal

# Verify installation
export PATH="$HOME/.local/bin:$PATH"
awslocal --version
```

## Start LocalStack and Postgres

```bash
# Start containers
docker-compose up -d postgres localstack

# Or use Makefile
make localstack-up
```

## Create S3 bucket and DynamoDB table

The Makefile automates this:

```bash
make init-local-resources
```

This will:
1. Start LocalStack and Postgres (if not running)
2. Wait for LocalStack to be healthy
3. Create S3 bucket `app-history-payloads`
4. Create DynamoDB table `EventIndexTable` with `pk`/`sk` keys

### Manual creation (if needed)

```bash
# Ensure awslocal uses eu-west-2 region
export AWS_REGION=eu-west-2
export AWS_DEFAULT_REGION=eu-west-2

# Create S3 bucket
awslocal s3 mb s3://app-history-payloads

# Create DynamoDB table (pk/sk)
awslocal dynamodb create-table \
  --table-name EventIndexTable \
  --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=sk,AttributeType=S \
  --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE \
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5
```

## Verify LocalStack resources

```bash
# Check LocalStack health
curl http://localhost:4566/health | jq

# List DynamoDB tables
awslocal dynamodb list-tables

# Describe the EventIndexTable
awslocal dynamodb describe-table --table-name EventIndexTable

# List S3 buckets
awslocal s3 ls
```

## Run the Spring Boot application

Ensure LocalStack and Postgres are running with resources created:

```bash
make init-local-resources
```

Then run the application:

```bash
export FEATURE_DISABLESECURITY=true
./gradlew :data-access-service:bootRun
```

The app is pre-configured in `application.yml` to connect to LocalStack at `http://localhost:4566` with dummy credentials (`test`/`test`).

## Test the DynamoDB event controller

Once the app is running, POST to the test endpoint:

```bash
curl -X POST http://localhost:8080/test/dynamo/events \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "eventType": "application",
    "description": "Test event from LocalStack"
  }'
```

With explicit eventId and timestamp:

```bash
curl -X POST http://localhost:8080/test/dynamo/events \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "eventType": "application",
    "eventId": "11111111-1111-1111-1111-111111111111",
    "timestamp": "2026-01-15T12:00:00Z",
    "description": "Test event with explicit ID"
  }'
```

### Sample request body (SaveEventRequest)

```json
{
  "eventType": "application",
  "eventId": "22222222-2222-2222-2222-222222222222",
  "timestamp": "2026-01-15T12:24:24.123Z",
  "description": "event with millis timestamp"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `eventType` | string | Yes | Entity type (e.g., "application", "proceeding") |
| `eventId` | UUID | No | Auto-generated if omitted |
| `timestamp` | ISO-8601 | No | Auto-generated if omitted |
| `description` | string | No | Event description |

## Verify saved events in DynamoDB

```bash
# Scan the table to see saved events
awslocal dynamodb scan --table-name EventIndexTable
```

## AWS configuration (application.yml defaults)

The app is pre-configured for LocalStack in `application.yml`:

```yaml
aws:
  region: ${AWS_REGION:eu-west-2}
  endpoint: ${AWS_ENDPOINT:http://localhost:4566}
  access-key: ${AWS_ACCESS_KEY:test}
  secret-key: ${AWS_SECRET_KEY:test}
  dynamodb:
    table-name: ${AWS_DYNAMODB_TABLE_NAME:EventIndexTable}
```

For production, override via environment variables:
- `AWS_REGION` — e.g., `eu-west-2`
- `AWS_ENDPOINT` — leave empty to use real AWS
- `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` — leave empty to use IRSA/default credentials

## Makefile targets

| Target | Description |
|--------|-------------|
| `make localstack-up` | Start LocalStack and Postgres containers |
| `make init-local-resources` | Start containers + create S3 bucket and DynamoDB table |
| `make localstack-down` | Stop containers and remove volumes |
| `make install-awslocal` | Install awscli-local via pipx |

## Cleanup

```bash
# Stop containers and remove volumes (clears all LocalStack data)
make localstack-down
```

## Troubleshooting

### "ResourceNotFoundException: Cannot do operations on a non-existent table"
- Run `make init-local-resources` to create the table
- Ensure LocalStack is running: `docker ps | grep localstack`
- Verify table exists: `awslocal dynamodb list-tables`
- Check region matches: table must be in `eu-west-2`

### "zsh: command not found: awslocal"
- Run `make install-awslocal` or install manually via pipx
- Add `~/.local/bin` to PATH: `export PATH="$HOME/.local/bin:$PATH"`
- Open a new terminal or run `source ~/.zshrc`

### App fails with "table not found" but table exists
- Ensure LocalStack DEFAULT_REGION matches app region (both should be `eu-west-2`)
- Reset LocalStack: `make localstack-down && make init-local-resources`
- Check startup logs for `DynamoDB config:` line showing endpoint and table name

### AWS CLI prompts for credentials
- Use `awslocal` instead (no credentials needed)
- Or export dummy credentials: `export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test`
