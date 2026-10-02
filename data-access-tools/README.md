# data-access-tools

`data-access-tools` creates realistic Data Access API test data through the public command API.

## Build

```zsh
./gradlew :data-access-tools:test :data-access-tools:installDist
```

The executable is written to:

```text
data-access-tools/build/install/data-access-tools/bin/data-access-tools
```

## Commands

All requests send the development token `Bearer swagger-caseworker-token` and the required `X-Service-Name: CIVIL_APPLY` header. The API uses this token's assigned Entra OID as the authenticated user for work-list assignments and decisions; do not supply a caseworker ID.

Applications are created sequentially. Each application uses randomly generated applicant, address, provider, and proceeding data (via [datafaker](https://www.datafaker.net/)); pass the root `--seed <long>` option to make a batch reproducible, otherwise a random seed is used each run. `create-autogranted` records an `AUTOGRANTED` outcome with a certificate. The granted and refused workflows record the `MANUAL` auto-grant outcome before making their decision. A batch continues after a failed application and exits non-zero if any item failed.

Prior-authority commands require `--type` with `EXPERT`, `DISBURSEMENT`, `COUNSEL`, or `ALL`; `--count` is the number created for each selected type. `create-drafts` requires an existing granted application and leaves the generated prior authorities as drafts. `create-submitted` creates an application, assigns it to the Swagger token's authenticated user, records a granted decision, creates its draft, saves valid type-specific content, and submits it. Each result line labels its `applicationId`, `priorAuthorityId`, and state.

Assignment uses the public work-list API and therefore requires the current `assignmentVersion`; it returns a conflict if the item changes before the write. The `local` commands read the JDBC-backed Axon `domain_event_entry` table directly. They default to the `axon` schema and `postgres` credentials, accept `--axon-schema`, `--db-username`, and `--db-password` overrides, and may display sensitive event payloads.

```zsh
# Create granted applications (submitted, assigned, decided GRANTED)
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-granted --count 10

# Create autogranted applications (no caseworker decision, granted with a certificate)
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-autogranted --count 10

# Create refused applications (submitted, assigned, decided REFUSED)
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-refused --count 10

# Create applications left awaiting a manual decision
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-manual --count 10

# Reproducible batch: same --seed produces the same random applicant/proceeding data
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 --seed 42 \
  applications create-granted --count 10

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

