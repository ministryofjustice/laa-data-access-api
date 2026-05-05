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

**Simple command:**
```bash
curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token
```

**Copy to clipboard (macOS):**
```bash
TOKEN=$(curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token) && echo $TOKEN | pbcopy && echo "✓ Token copied to clipboard"
```

**For Swagger UI:**
1. Run the command above
2. Open http://localhost:8080/swagger-ui.html
3. Click **Authorize** button
4. Paste the token (no "Bearer " prefix needed)
5. Click **Authorize**
6. Make requests!

---

### Use a Token

**With curl:**
```bash
TOKEN=$(curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token)

curl http://localhost:8080/api/access/caseworkers \
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

**Missing required parameters:**
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

**Required parameters:**
- `Content-Type: application/x-www-form-urlencoded` header
- `grant_type=client_credentials`
- `client_id=<anything>` (e.g., "test")
- `client_secret=<anything>` (e.g., "test")

---

### Mock server not running

**Check if running:**
```bash
docker ps | grep mock-oauth2
```

**Start it:**
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

**Check the token has required claims:**
```bash
echo $TOKEN | awk -F. '{print $2}' | base64 -d 2>/dev/null | jq
```

Should show:
- `aud: ["laa-data-access-api"]`
- `roles: ["LAA_CASEWORKER"]`
- `LAA_APP_ROLES: "LAA_CASEWORKER"`

---

## Common Commands

**Get fresh token and test endpoint:**
```bash
TOKEN=$(curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token) && \
curl http://localhost:8080/api/access/caseworkers \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY" | jq
```

**Decode a token to inspect claims:**
```bash
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

