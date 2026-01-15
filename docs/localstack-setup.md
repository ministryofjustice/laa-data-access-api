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
```

## Create S3 bucket and DynamoDB table

When the application is started with the `local` Spring profile, it will automatically create the required `app-history-payloads` S3 bucket and `EventIndexTable` DynamoDB table in LocalStack on startup.

This removes the need for any manual setup. The application code ensures that the necessary infrastructure is available before it's needed.

You can verify the resources were created after starting the app:
```bash
# Verify S3 bucket
awslocal s3 ls

# Verify DynamoDB table
awslocal dynamodb list-tables
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

Ensure LocalStack and Postgres are running.

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

## Cleanup

To stop the containers and remove the persistent volumes (which clears all LocalStack and Postgres data), run:
```bash
docker-compose down -v
```

## Troubleshooting

### "ResourceNotFoundException: Cannot do operations on a non-existent table"
- This error should not occur if the `local` profile is active, as the application creates the table on startup.
- Ensure the `local` Spring profile is enabled when running the application.
- Ensure LocalStack is running before you start the application: `docker ps | grep localstack`
- Verify the table was created after the app started: `awslocal dynamodb list-tables`
- Check the application logs for any errors during the resource creation process.

### "zsh: command not found: awslocal"
- Install `awslocal` manually via pipx: `pipx install awscli-local`
- Add `~/.local/bin` to PATH: `export PATH="$HOME/.local/bin:$PATH"`
- Open a new terminal or run `source ~/.zshrc`

### App fails with "table not found" but table exists
- Ensure LocalStack DEFAULT_REGION matches app region (both should be `eu-west-2`)
- Reset LocalStack: `docker-compose down -v && docker-compose up -d` and then re-run the resource creation scripts.
- Check startup logs for `DynamoDB config:` line showing endpoint and table name

### AWS CLI prompts for credentials
- Use `awslocal` instead (no credentials needed)
- Or export dummy credentials: `export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test`
