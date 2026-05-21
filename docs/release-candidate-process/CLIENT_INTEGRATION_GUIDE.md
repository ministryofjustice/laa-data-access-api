# Client Integration Guide: Release Candidate (RC) Program

**For**: External Clients Testing RC Environments  
**Last Updated**: 21 May 2026

---

## Welcome to the RC Program

We're excited to invite you to our **Release Candidate (RC) Program**. This program gives you early access to new features before they're released to our Staging environment, so you can validate compatibility and prepare your integration in advance.

---

## What is an RC?

A **Release Candidate (RC)** is a stable, snapshot version of our API deployed to a dedicated environment for testing. RCs contain features that are about to be released but haven't reached our client-facing Staging environment yet.

### Key Points

✅ **Stable**: Thoroughly tested internally before RC creation  
✅ **Predictable**: Released on a schedule you can plan around  
✅ **Time-bound**: Typically available for 1-2 weeks before Staging release  
✅ **Production-like**: Same infrastructure, configuration, and behavior as Staging  
❌ **Not for production**: RC is for testing only; always use Staging for live integrations  

---

## Quick Start

### 1. You'll Receive a Notification

When an RC is ready, you'll get an email with:
- RC endpoint URL
- What's new (release notes)
- Testing timeline
- Support contact information

### 2. Update Your Test Environment

Point your test client to the RC endpoint:

```
Old (Staging):
https://laa-data-access-api-staging.cloud-platform.service.justice.gov.uk

New (RC):
https://<rc-name>-uat.cloud-platform.service.justice.gov.uk
```

### 3. Run Your Tests

Execute your integration test suite against the RC:

```bash
# Example: Run your integration tests against RC
TEST_API_URL="https://<rc-name>-uat.cloud-platform.service.justice.gov.uk" \
  npm test  # or your test command

# Report any failures (see Reporting Issues below)
```

### 4. Validate Changes

Review the release notes and confirm:
- ✅ All endpoints you use are working
- ✅ No breaking changes affecting your integration
- ✅ Response formats are as expected
- ✅ Error handling is clear

### 5. Report Feedback

Let us know how it went (see Reporting Issues & Feedback below)

---

## Understanding RC Versions

RCs use semantic versioning with a `-rc.N` suffix:

```
v1.2.3-rc.1  ← First RC candidate for version 1.2.3
v1.2.3-rc.2  ← Second candidate (fixes applied, retesting required)
v1.2.3       ← Final release to Staging (same code as rc.2)
```

### What Happens to Old RCs?

When a new RC is created:
- **Previous RC removed** from the live environment
- **New RC deployed** for testing
- **Timeline**: Typically 1-2 weeks between RCs or until final release

---

## What to Test

### Recommended Testing Checklist

- [ ] **Endpoints you use** work correctly
  - Test all endpoints your integration calls
  - Verify response format matches documentation
  - Check HTTP status codes
  
- [ ] **Authentication** still works
  - Your API keys/credentials are accepted
  - Authorization rules unchanged (or documented)
  
- [ ] **Error handling**
  - Invalid requests return expected errors
  - Error messages are helpful
  - Error codes match documentation
  
- [ ] **Data formats**
  - Request/response JSON structures
  - Data types (strings, numbers, dates)
  - Null/empty field handling
  
- [ ] **Performance**
  - Response times acceptable (benchmark against Staging)
  - No unexpected timeouts
  - Load handling (if applicable)
  
- [ ] **Breaking changes** (from release notes)
  - Deprecated endpoints removed?
  - Field names changed?
  - New required fields?

### Example Test Scenarios

**Scenario 1: Create a Record**
```bash
curl -X POST https://<rc-endpoint>/api/records \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Record", "type": "application"}'

# Verify:
# - Returns 201 Created (or 200 OK)
# - Response includes record ID
# - Record appears when listing
```

**Scenario 2: Retrieve with Filters**
```bash
curl "https://<rc-endpoint>/api/records?status=active&limit=10" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Verify:
# - Returns 200 OK
# - Response is valid JSON
# - Filters work as documented
# - Pagination is clear
```

**Scenario 3: Error Handling**
```bash
curl -X POST https://<rc-endpoint>/api/records \
  -H "Authorization: Bearer INVALID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'

# Verify:
# - Returns 401 Unauthorized (not 500 Internal Error)
# - Error message is helpful
# - Error structure matches docs
```

---

## Release Notes: What to Look For

RC announcements include **Release Notes** highlighting:

### Breaking Changes
⚠️ **Pay special attention to these.** They may require code changes.

Example:
```
BREAKING: POST /api/records now requires 'status' field
- Old: POST /api/records { "name": "..." }
- New: POST /api/records { "name": "...", "status": "active" }
- Migrate: Add status="active" to all record creations
```

### New Endpoints or Functionality
✨ New features you might want to adopt.

Example:
```
NEW: GET /api/records/{id}/related
- Returns related records for given ID
- Useful for pre-loading related data
- See: /docs/api#related-records
```

### Deprecations
⏰ Old features still work but will be removed in a future release.

Example:
```
DEPRECATED: POST /api/search endpoint (use GET /api/records?q=... instead)
- Old endpoint still works (returns 200)
- Will be removed in v2.0 (3+ months notice)
- Migrate: Update to use new query endpoint
```

### Bug Fixes
🐛 Non-breaking fixes to existing behavior.

Example:
```
FIXED: Sorting by created_at now returns results in correct order
- Previously: Returned in random order
- Now: Sorted ascending by default
- Impact: Low (edge case fix)
```

---

## Reporting Issues & Feedback

### Found a Problem?

Please report it! Issues found in RC help us catch bugs before Staging release.

**How to report:**

1. **Email** (preferred for most issues)
   - To: [support email]
   - Subject: `[RC v1.2.3-rc.1] Issue: [Brief description]`
   - Include:
     - What you were trying to do
     - Expected behavior
     - Actual behavior
     - Steps to reproduce
     - Request/response examples (if applicable)

2. **Slack** (for urgent/blocking issues)
   - Channel: [#laa-data-access-support]
   - Mention: @[on-call engineer]
   - Include same details as email

### Example Issue Report

```
Subject: [RC v1.2.3-rc.1] Issue: POST /api/records returns 500 error

Steps to reproduce:
1. Create a POST request with the following JSON:
   {
     "name": "Test Record",
     "type": "application",
     "status": "active"
   }

2. Send to: https://rc-main-laa-data-access-api-uat.cloud-platform.service.justice.gov.uk/api/records
3. Include Authorization header with valid token

Expected behavior:
- Returns 201 Created
- Response includes record ID

Actual behavior:
- Returns 500 Internal Server Error
- Error message: "Internal server error"

Environment:
- RC version: v1.2.3-rc.1
- Client library: Your client name + version
- Request payload: [paste above]
- Response payload: 500 error (no JSON body)
```

### Issue Response SLA

| Severity | Acknowledge | Resolve |
|----------|---|---|
| **Blocking** | <2 hours | <24 hours |
| **High** | <4 hours | <48 hours |
| **Medium** | <1 day | <1 week |
| **Low** | <2 days | Before final release |

### Types of Feedback Welcome

- **Bug reports**: Something's broken
- **Documentation feedback**: Docs unclear, typos
- **Performance observations**: RC slower than expected
- **Feature requests**: Nice-to-have improvements
- **General feedback**: What worked well, what didn't

---

## Troubleshooting

### RC Endpoint Unreachable

```bash
# Verify URL is correct
curl -I https://<rc-name>-uat.cloud-platform.service.justice.gov.uk

# If DNS error: Wait 5-10 minutes (DNS propagation)
nslookup <rc-name>-uat.cloud-platform.service.justice.gov.uk

# If connection refused: RC may be starting up (wait 5-10 minutes)
```

### Authentication Failing

```bash
# Verify your token is valid
curl -H "Authorization: Bearer YOUR_TOKEN" https://<rc-endpoint>/api/health

# If 401 Unauthorized:
# - Check token format (usually "Bearer <token>")
# - Verify token hasn't expired
# - Check if special test tokens needed for RC (ask support)
```

### Unexpected Errors

```bash
# Include full error in report (not just "error")
curl -v https://<rc-endpoint>/api/records \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "test"}'

# Copy the full response, including headers
```

### Data Issues

```bash
# RC uses a fresh database (not Staging data)
# If you need specific test data:
# 1. Create it on RC (via API)
# 2. Or request test fixtures from support team
```

---

## Best Practices

### Before Testing

- ✅ **Read release notes** to understand what changed
- ✅ **Plan your testing** (what scenarios matter to you?)
- ✅ **Allocate time** (1-2 hours of testing recommended)
- ✅ **Use a test environment** (don't run RC tests against production)

### While Testing

- ✅ **Document issues** with steps to reproduce
- ✅ **Test critical paths** (most important workflows)
- ✅ **Check error scenarios** (what if something fails?)
- ✅ **Compare with Staging** (is behavior different?)

### After Testing

- ✅ **Report findings** (even if all OK, let us know)
- ✅ **Confirm timeline** (are you ready when RC moves to Staging?)
- ✅ **Plan migration** (if breaking changes, plan your update)
- ✅ **Ask questions** (if docs unclear, tell us!)

---

## FAQ

**Q: Will my production integration break when RC becomes Staging?**  
A: No. RC and Staging will have the same code. If you test thoroughly on RC, no surprises in Staging.

**Q: Can I use RC for production traffic?**  
A: No. RC is for testing only. It's not monitored for production SLAs. Always use Staging for production integrations.

**Q: What if RC is unavailable during my testing window?**  
A: Contact support immediately. We aim for 99% uptime but may need to restart for fixes. We'll notify you in advance if possible.

**Q: Can I test for longer than 1-2 weeks?**  
A: Yes, but be aware RC will be removed when next RC or final release is created. Plan accordingly.

**Q: How do I get notified of new RCs?**  
A: You'll receive emails. Confirm you're on the mailing list with your account manager or support contact.

**Q: What happens to my RC test data?**  
A: It's deleted when RC is removed. RC is purely for testing; don't rely on persistence.

**Q: Is RC data privacy same as Staging?**  
A: Yes. RC uses production-like security. Don't use real sensitive data; use test data instead.

**Q: Can I have multiple people on my team test RC?**  
A: Yes! The RC endpoint is accessible to your entire team using your API credentials.

**Q: What if we find a bug in RC?**  
A: Report it! We'll fix it and create a new RC (v1.2.3-rc.2). You can re-test the fix.

---

## Getting Help

### Before You Reach Out

Check:
1. **Release notes** (might explain behavior)
2. **API documentation** (might clarify endpoint)
3. **Troubleshooting section** (above)

### Contact Points

| Question Type | Contact | Response Time |
|---|---|---|
| **Technical issue** | [support email] | <2h (critical) |
| **Account/access** | [account manager email] | <4h |
| **Feature request** | [product manager email] | <1 day |
| **Emergency blocker** | [on-call phone] | <30 min |

### Sample Support Email

```
To: [support email]
Subject: [RC v1.2.3-rc.1] Question: How to handle new status field?

Hi team,

We're testing RC v1.2.3-rc.1 and noticed a new "status" field in the 
/api/records response. The release notes mention it's required for POST 
but don't explain the valid values.

Questions:
1. What are valid values for status?
2. Is there a default if not provided?
3. Can it be changed after record creation?

Request/response examples attached.

Thanks,
[Your Name]
```

---

## What Happens After RC Testing

### Timeline

1. **RC Available**: Today (date)
2. **Your Testing Window**: Next 1-2 weeks
3. **Final RC Promotion**: (date) — RC becomes final release
4. **Staging Release**: (date) — Code deployed to Staging
5. **Your Production Update**: (your timeline)

### Your Checklist Before Staging Release

- [ ] RC testing complete
- [ ] All blocking issues resolved
- [ ] Integration code ready (if breaking changes)
- [ ] Team trained on new features
- [ ] Go/no-go decision made
- [ ] Staging release date confirmed

### Questions?

Reach out to [support email] or [account manager]. We're here to help!

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 21 May 2026 | Initial release |

