# Quick Reference: Getting Test Tokens

**For developers who just need to get a token quickly** 🚀

---

## Local Development

### Start the Application (Frictionless!)

```bash
./gradlew bootRun
```

This automatically starts the mock-oauth2-server and the application. No Docker commands needed!

---

### Get a Token

**Easiest — use the script (auto-starts mock server if not running):**
```bash
./scripts/get-token.sh
```

**Copy straight to clipboard (macOS):**
```bash
./scripts/get-token.sh --copy
```

**Capture into a variable for immediate use:**
```bash
TOKEN=$(./scripts/get-token.sh)
```

**Decode the token payload to inspect claims:**
```bash
./scripts/get-token.sh --decode
```

**All options:**
```
./scripts/get-token.sh [environment] [options]

Environments:
  local   Local development (default) — mock server on port 9999
  smoke   Smoke test infrastructure   — mock server on port 9998
  custom  Use OAUTH_TOKEN_URL env var

Options:
  -c, --copy    Copy token to clipboard
  -d, --decode  Decode and pretty-print the token payload
  -h, --help    Show full usage
```

---

<details>
<summary>Manual curl alternative</summary>

```bash
TOKEN=$(curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token)
```

</details>

---

**For Swagger UI:**
1. Run `./scripts/get-token.sh --copy`
2. Open http://localhost:8080/swagger-ui.html
3. Click **Authorize** button
4. Paste the token (no "Bearer " prefix needed)
5. Click **Authorize**
6. Make requests!

---

### Use a Token

```bash
TOKEN=$(./scripts/get-token.sh)

curl http://localhost:8080/api/v0/caseworkers \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY"
```

---

## Why client_id and client_secret?

The mock-oauth2-server follows the OAuth2 spec which requires `client_id` and `client_secret` for the `client_credentials` grant type. However, it **doesn't validate them**—you can use any values (e.g., "test", "test").

This differs from production where real Azure AD credentials are required.

---

## Troubleshooting

### "null" response when fetching token

**Missing required parameters (manual curl only):**
```bash
# ❌ This returns null:
curl -s -X POST http://localhost:9999/entra/token \
  -d "grant_type=client_credentials" | jq -r .access_token

# ✅ This works:
curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token
```

> The script handles all of this for you automatically.

**Required parameters:**
- `Content-Type: application/x-www-form-urlencoded` header
- `grant_type=client_credentials`
- `client_id=<anything>` (e.g., "test")
- `client_secret=<anything>` (e.g., "test")

---

### Mock server not running

The script will detect this and start the mock server automatically. If you need to start it manually:

```bash
docker compose up -d mock-oauth2-server
```

**Or just run bootRun** (it starts automatically):
```bash
./gradlew bootRun
```

---

### 401 Unauthorized when calling API

**Check you have the token:**
```bash
echo $TOKEN
# Should output a long JWT string
```

**Inspect claims (script shortcut):**
```bash
./scripts/get-token.sh --decode
```

**Or manually:**
```bash
echo $TOKEN | awk -F. '{print $2}' | base64 -d 2>/dev/null | jq
```

Should show:
- `aud: ["laa-data-access-api"]`
- `roles: ["LAA_CASEWORKER"]`
- `LAA_APP_ROLES: "LAA_CASEWORKER"`

> **Note:** Tokens expire after 1 hour. Re-run `./scripts/get-token.sh` to get a fresh one.

---

## Common Commands

**Get token and test endpoint in one line:**
```bash
TOKEN=$(./scripts/get-token.sh) && \
curl http://localhost:8080/api/v0/caseworkers \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY" | jq
```

**Smoke test environment:**
```bash
TOKEN=$(./scripts/get-token.sh smoke) && \
curl http://localhost:9000/api/v0/caseworkers \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY" | jq
```

**Decode a token to inspect claims:**
```bash
./scripts/get-token.sh --decode
# or for an existing $TOKEN variable:
echo $TOKEN | awk -F. '{print $2}' | base64 -d 2>/dev/null | jq
```

**Stop everything:**
```bash
docker compose down
# Or keep database running and only stop mock server:
docker stop laa-mock-oauth2
```

---

## Full Documentation

For complete details, see:
- [Usage Guide](./mock-oauth2-server-usage-guide.md) - Comprehensive guide for all environments
- [README](../../README.md) - Getting started with local development

---

