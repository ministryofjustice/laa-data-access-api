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
  --api-url http://localhost:8080 \
  applications create-granted --count 10

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8080 \
  applications create-refused --count 10

data-access-tools/build/install/data-access-tools/bin/data-access-tools \
  --api-url http://localhost:8080 \
  prior-authorities create-all --application-id 123e4567-e89b-12d3-a456-426614174000
```

Applications are created sequentially. Each workflow records the `MANUAL` auto-grant outcome before making its granted or refused decision. A batch continues after a failed application and exits non-zero if any item failed.

