# flagd + OpenFeature local POC

## What this proves
- `flagd` can run locally from a JSON flags file.
- The Spring Boot app can evaluate flags through OpenFeature when `/flags` is called.
- Default values are returned safely if `flagd` is disabled.

## Local run

```bash
docker compose -f docker-compose.flagd-poc.yml up --build
```

Or use the helper script:

```bash
./scripts/run-flagd-poc.sh start
./scripts/run-flagd-poc.sh run-app
./scripts/run-flagd-poc.sh check
```

Then visit:

```bash
curl http://localhost:8080/flags
```

## Change a flag

Edit `flagd/flags.flagd.json`, for example change `poc-enabled.defaultVariant` from `off` to `on`, then restart `flagd` and the app.

```bash
./scripts/run-flagd-poc.sh restart
```

## What the app does

`FeatureFlagsController` exposes `/flags` and evaluates the following flags at request time:
- `poc-enabled` as a boolean
- `poc-variant` as a string variant

## Validation checklist
- Start `flagd` and the Spring Boot app
- Call `/flags`
- Confirm `pocEnabled` and `pocVariant`
- Update the flag definition
- Restart `flagd` and the app
- Confirm the updated values are returned


