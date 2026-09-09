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

All requests send the Development Token `Bearer swagger-caseworker-token` and the required `X-Service-Name: CIVIL_APPLY` header.

```zsh
data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-granted --count 10

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-autogranted --count 10

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-refused --count 10

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications create-manual --count 10

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications make-decision \
  --application-id 123e4567-e89b-12d3-a456-426614174000 \
  --decision GRANTED

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  prior-authorities create-all --application-id 123e4567-e89b-12d3-a456-426614174000

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  applications assign --application-id 123e4567-e89b-12d3-a456-426614174000 \
  --caseworker-id 123e4567-e89b-12d3-a456-426614174001 \
  --expected-assignment-version 0

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8082 \
  prior-authorities assign --prior-authority-id 123e4567-e89b-12d3-a456-426614174002 \
  --caseworker-id 123e4567-e89b-12d3-a456-426614174001 \
  --expected-assignment-version 0

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  local application-events --jdbc-url jdbc:postgresql://localhost:5432/data_access_api \
  --application-id 123e4567-e89b-12d3-a456-426614174000

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  local prior-authority-events --jdbc-url jdbc:postgresql://localhost:5432/data_access_api \
  --prior-authority-id 123e4567-e89b-12d3-a456-426614174002
```

Applications are created sequentially. `create-autogranted` records an `AUTOGRANTED` outcome with a certificate. The granted and refused workflows record the `MANUAL` auto-grant outcome, assign caseworker `8a082fe2-d539-4177-aae3-7498fd5904c7`, and then make their decision. `create-manual` only records the `MANUAL` outcome: it does not assign a caseworker or make a decision. `make-decision` retrieves the application and applies the requested decision to all of its proceedings. A batch continues after a failed application and exits non-zero if any item failed.

Assignment uses the public work-list API and therefore requires the current `assignmentVersion`; it returns a conflict if the item changes before the write. The `local` commands read the JDBC-backed Axon `domain_event_entry` table directly. They default to the `axon` schema and `postgres` credentials, accept `--axon-schema`, `--db-username`, and `--db-password` overrides, and may display sensitive event payloads.

