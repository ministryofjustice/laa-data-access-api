# data-access-tools

`data-access-tools` creates realistic Data Access API test data through the public command API.

## Build

```zsh
./gradlew :data-access-tools:test :data-access-tools:installDist
```

The build requires JDK 25.

The executable is written to:

```text
data-access-tools/build/install/data-access-tools/bin/data-access-tools
```

## Commands

All requests send the development token `Bearer swagger-caseworker-token` and the required `X-Service-Name: CIVIL_APPLY` header. The API uses this token's assigned Entra OID as the authenticated user for work-list assignments and decisions; do not supply a caseworker ID.

Applications are created sequentially through the draft API, not the deprecated direct application POST. Each application uses randomly generated applicant, address, provider, and proceeding data (via [datafaker](https://www.datafaker.net/)); pass the root `--seed <long>` option to make the generated random fields reproducible, otherwise a random seed is used each run. IDs and timestamps are generated independently. API-backed commands require the root `--api-url` option.

`applications create-draft --count N` creates complete, validated drafts without submitting them. Each result prints the `applicationId`, LAA reference, and `APPLICATION_DRAFT` lifecycle state. No additional application content is required before submission. The payload retains the intended final business status `APPLICATION_SUBMITTED`; draft lifecycle is separate from that status, and the API preserves it when submitting.

`applications submit-draft --application-id UUID` submits an existing draft without replacing its content. Upload evidence through `POST /api/v0/applications/{id}/documents` before submitting, using multipart `file` and `documentType` fields. Submission seals the content and preserves uploaded evidence metadata. Uploads after submission are rejected. These tools do not provide an evidence-upload command.

`applications create --count N [--outcome submitted|manual|autogranted|granted|refused]` creates and submits complete drafts, then applies the selected outcome. The default `submitted` stops after submission. `manual` records a `MANUAL` auto-grant outcome without assignment or decision; `autogranted` records an `AUTOGRANTED` outcome with a certificate. `granted` and `refused` record `MANUAL`, assign the application, and make the corresponding decision. Outcome values are case-insensitive.

Draft creation accepts HTTP `201` or `202`; submission accepts `200` or `202`. A successful submission can precede read-model availability, so `GET /api/v0/applications/{id}` may briefly return `404`. Outcome workflows wait up to 30 seconds for that read model, retrying only `404` responses before continuing. A batch continues after a failed application and exits non-zero if any item failed. Failures include the application ID, failed stage, and last confirmed lifecycle state. A confirmed draft whose submission fails can be submitted later with `submit-draft`; do not recreate it. If a write times out, verify its result before retrying.

The previous creation commands have been removed, without compatibility aliases:

| Previous command | Replacement |
| --- | --- |
| `applications create-manual --count N` | `applications create --count N --outcome manual` |
| `applications create-autogranted --count N` | `applications create --count N --outcome autogranted` |
| `applications create-granted --count N` | `applications create --count N --outcome granted` |
| `applications create-refused --count N` | `applications create --count N --outcome refused` |

Prior-authority commands require `--type` with `EXPERT`, `DISBURSEMENT`, `COUNSEL`, or `ALL`; `--count` is the number created for each selected type. `create-drafts` requires an existing granted application and leaves the generated prior authorities as drafts. `create-submitted` creates and submits a parent application through the draft API, waits for its read model, assigns it to the Swagger token's authenticated user, records a granted decision, creates a prior-authority draft, saves valid type-specific content, and submits it. Each result line labels its `applicationId`, `priorAuthorityId`, and state.

Assignment uses the public work-list API and therefore requires the current `assignmentVersion`; it returns a conflict if the item changes before the write. The `local` commands read the JDBC-backed Axon `domain_event_entry` table directly. They default to the `axon` schema and `postgres` credentials, accept `--axon-schema`, `--db-username`, and `--db-password` overrides, and may display sensitive event payloads.

```zsh
# Create complete drafts, leaving them available for evidence uploads
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-draft --count 1

# Upload evidence to the printed draft ID through the API before submission
curl --fail-with-body \
  -H 'Authorization: Bearer swagger-caseworker-token' \
  -H 'X-Service-Name: CIVIL_APPLY' \
  -F 'file=@/path/to/evidence.pdf;type=application/pdf' \
  -F 'documentType=GATEWAY_EVIDENCE' \
  http://localhost:8082/api/v0/applications/123e4567-e89b-12d3-a456-426614174000/documents

# Submit that draft without replacing its evidence or application content
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications submit-draft --application-id 123e4567-e89b-12d3-a456-426614174000

# Create and submit applications without applying an outcome
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create --count 10

# Create granted applications (submitted, assigned, decided GRANTED)
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create --count 10 --outcome granted

# Create autogranted applications (no caseworker decision, granted with a certificate)
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create --count 10 --outcome autogranted

# Create refused applications (submitted, assigned, decided REFUSED)
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create --count 10 --outcome refused

# Create applications left awaiting a manual decision
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create --count 10 --outcome manual

# Reproducible batch: same --seed produces the same random applicant/proceeding data
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 --seed 42 \
  applications create --count 10 --outcome granted

# Create draft prior authorities of all types against an existing granted application
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  prior-authorities create-drafts --application-id 44ade549-09c8-432e-90ff-1fe18f39624a \
  --type ALL --count 2

# Create submitted EXPERT prior authorities end-to-end (own application, assignment, decision)
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  prior-authorities create-submitted --type EXPERT --count 10

# Assign an application work-list item to the Swagger token's authenticated user
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications assign --application-id 123e4567-e89b-12d3-a456-426614174000 \
  --expected-assignment-version 0

# Assign a prior-authority work-list item to the Swagger token's authenticated user
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  prior-authorities assign --prior-authority-id 123e4567-e89b-12d3-a456-426614174002 \
  --expected-assignment-version 0

# Read an application's raw Axon domain events from the event store
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  local application-events --jdbc-url jdbc:postgresql://localhost:5432/data_access_api \
  --application-id 123e4567-e89b-12d3-a456-426614174000

# Read a prior authority's raw Axon domain events from the event store
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  local prior-authority-events --jdbc-url jdbc:postgresql://localhost:5432/data_access_api \
  --prior-authority-id 123e4567-e89b-12d3-a456-426614174002
```

