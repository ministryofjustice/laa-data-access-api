# OpenAPI Discriminator Approach: Technical Assessment

**Date:** 2025-03-25
**Branch:** DSTEW-1171-Open_API-Discriminator-POC
**Status:** Functional with significant brittleness in example management
**Focus:** Assessment of current implementation, costs, and risks

---

## Overview

The current branch implements OpenAPI discriminator support for versioned `applicationContent` with the following architecture:

- **Versioned spec files**: `/data-access-api/v1/`, `/v2/`, `/common/` directories
- **Discriminator pattern**: `oneOf` with `discriminator.propertyName: objectType`
- **Type classes**: `ApplyApplication`, `ApplyApplication1`, `DecideApplication`
- **Gradle automation**: `copyOpenApiSpecs` task copies specs to resources
- **HTTP serving**: `OpenApiResourceController` serves specs at HTTP endpoints
- **Security exemptions**: `/open-api-specification.yml`, `/v1/**`, `/v2/**`, `/common/**` excluded from auth
- **Test results**: All 480 tests passing (234 unit + 246 integration)

---

## What Works Well ✅

| Aspect | Status | Notes |
|--------|--------|-------|
| **Versioned specs** | ✅ | Clear directory structure (v1, v2, common) |
| **Example organization** | ✅ | Examples grouped by version |
| **Discriminator in OpenAPI** | ✅ | Swagger shows variants |
| **Gradle automation** | ✅ | Single-source maintenance of specs |
| **All tests passing** | ✅ | 480 tests, all green |
| **Backwards compatible** | ✅ | Main branch functionality preserved |

---

## The Problems ❌

### 1. Test Data Complexity

**Current implementation:**
```java
// 7-10 lines per test
ApplicationContent content = applicationContentFactory.create();
ApplyApplication applyApp = new ApplyApplication();
applyApp.setObjectType("apply");
applyApp.setId(content.getId());
applyApp.setSubmittedAt(OffsetDateTime.parse(content.getSubmittedAt()));
if (content.getAllLinkedApplications() != null) {
    applyApp.putAdditionalProperty("allLinkedApplications",
        content.getAllLinkedApplications());
}
```

**Alternative (Map-based):**
```java
// 2-3 lines
ApplicationContent content = applicationContentFactory.create();
Map<String, Object> contentMap =
    MapperUtil.getObjectMapper().convertValue(content, Map.class);
```

**Impact:**
- Current: ~50 tests × 7 lines = **350-500 lines of boilerplate**
- Alternative: ~50 tests × 2 lines = **100 lines total**
- **Difference: +400 lines of unnecessary code**

### 2b. Type Class Proliferation

**Current approach requires 4 classes:**
```
ApplicationContent          (base POJO)
ApplyApplication           (v1 apply - extends BaseApplicationContent)
ApplyApplication1          (v2 apply - extends BaseApplicationContent)
DecideApplication          (decide variant - extends BaseApplicationContent)
```

**Why this is a problem:**
- Adding a field to ApplicationContent requires changes to 3 type classes
- Each type class must be kept in sync
- New versions require new type class creation
- 4× the maintenance burden for every schema change

### 2c. Infrastructure Complexity (Specs Serving & Security)

**This approach requires additional infrastructure not present in main branch:**

```java
// 1. Gradle build task to copy specs
task copyOpenApiSpecs(type: Copy) {
    from '../data-access-api'
    include '*.yml'
    include 'v1/**/*.yml'
    include 'v2/**/*.yml'
    include 'common/**/*.yml'
    into 'src/main/resources'
}

// 2. Controller to serve spec files at HTTP endpoints
@RestController
public class OpenApiResourceController {
    @GetMapping("/{version}/{filename:.+}")
    public ResponseEntity<Resource> getSpecFile(
        @PathVariable String version,
        @PathVariable String filename) {
        // Serve versioned spec files via HTTP
    }
}

// 3. Security config updates (exemptions for spec endpoints)
http.authorizeHttpRequests(authorize -> authorize
    .requestMatchers(
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/open-api-specification.yml",
        "/open-api-application-specification.yml",
        "/common/**",      // ← Must maintain these patterns
        "/v1/**",          // ← when adding versions
        "/v2/**")          // ← grows with each version
    .permitAll()
    // ...
)
```

**Infrastructure complexity:**
- ✅ Gradle build process more complex (copy step added)
- ✅ New controller for spec serving (must be maintained)
- ✅ Security config must grow (more unauthenticated endpoints per version)
- ✅ Security matrix to manage (who can access specs, when, how)
- ✅ Testing of spec endpoints needed (separate from API testing)

**Ongoing maintenance:**
- Added 2 files (Gradle task, Controller)
- Modified 1 file (SecurityConfig)
- When adding new version: Update security config paths (30 min)
- When security policy changes: Must consider spec endpoints

### 3. Brittle Example Management

**The core problem:**
Examples in YAML spec files are **completely disconnected from code**. Nothing ensures they stay valid.

#### Scenario: Adding Required Field

```java
// Developer adds field to ApplicationContent
ApplicationContent.java:
  + @NotNull private String reasonForApplication;

// What happens to examples?
// NOTHING. They're static YAML.
```

**Current state of examples:**
```yaml
# /data-access-api/v1/open-api-apply-application.yml
v1_apply_simple:
  value:
    objectType: "apply"
    id: "550e8400-e29b-41d4-a716-446655440000"
    submittedAt: "2024-03-19T10:30:00Z"
    # ← Missing reasonForApplication field
    # ← No automated check

# Developer ships this. Consumer copies example.
# Consumer gets validation error: "reasonForApplication is required"
# Support ticket: "Your documentation is broken!"
```

#### Why Examples Are Brittle

| Risk | Impact | Current Mitigation |
|------|--------|-------------------|
| **Missing required fields** | Example fails at validation | None (manual review) |
| **Stale field values** | Example shows old enum | None (manual review) |
| **Changed data types** | Example has wrong format | None (manual review) |
| **Removed optional fields** | Example references non-existent field | None (manual review) |
| **Incompatible versions in one file** | Mixed v1/v2 confusion | Directory structure helps, but not foolproof |

**Bottom line:** Examples are **best-effort documentation**, not validated guarantees.

---

## Effort Required for Current Approach

### What Was Done (Already Completed)

| Task | Effort | Status |
|------|--------|--------|
| Spec versioning infrastructure (v1, v2, common dirs) | 1h | ✅ Done |
| Discriminator in specs (oneOf + discriminator field) | 2h | ✅ Done |
| Type class creation (ApplyApplication, etc.) | 1.5h | ✅ Done |
| Integration test fixes (Map → type object conversions) | 2h | ✅ Done |
| Gradle copy task (copyOpenApiSpecs) | 1h | ✅ Done |
| OpenApiResourceController (spec serving) | 1h | ✅ Done |
| SecurityConfig updates (exempt spec paths from auth) | 1h | ✅ Done |
| Swagger security scheme fix | 1h | ✅ Done |
| **Subtotal** | **10.5h** | |

### What's Still Needed

#### 1. Example Validation Tests (1-2 hours)

To catch stale examples, must add manual validation tests:

```java
@Test void example_v1_apply_simple_is_valid() {
    String exampleYaml = loadExample(
        "data-access-api/v1/open-api-apply-application.yml",
        "v1_apply_simple"
    );
    Map<String, Object> example = yamlToMap(exampleYaml);

    // Manual validation approach:
    // assertNotNull(example.get("id"))
    // assertNotNull(example.get("submittedAt"))
    // Validate against ApplyApplication schema
    // - Brittle (depends on JSR-303 annotations)
    // - Must update when validation rules change
    // - Doesn't catch all schema mismatches
}
```

**Effort:**
- No validation: 0 hours (current state: risky)
- Manual validation: 1-2 hours (not robust, brittle)

#### 2. Documentation for Consumers (1-2 hours)

Consumers need to understand discriminator:
- "What is objectType?"
- "How do I know which variant to use?"
- "When does it change?"

Documentation effort: 1-2 hours

#### 3. Maintenance Per Feature Addition (2-3 hours each)

When adding a field (e.g., `reasonForApplication`):

```
Step 1: Update ApplicationContent.java        15 min
Step 2: Update ApplyApplication.java          15 min
Step 3: Update ApplyApplication1.java         15 min
Step 4: Update DecideApplication.java         15 min
Step 5: Update generators                     30 min
Step 6: Update 50+ tests                      45 min
Step 7: Update spec examples manually         30 min
Step 8: If example breaks, debug & fix        30 min
Step 9: Run tests, fix validation assertions  30 min
──────────────────────────────────────────────────────
Total: 2.5-3 hours per field addition
```

This is the recurring cost each time the schema evolves.

#### 4. Validation Message Fragility (1-2 hours initially, ongoing)

Typed objects use JSR-303 nested validation, producing different messages than Map:

```java
// Before (Map): "Generic Validation Error: size must be between..."
// After (ApplyApplication): "requestValidationFailed: applicationContent.id must not be null"

// Tests break when validation changes
// Must update expected messages
```

**Effort:**
- Initial fixes: Done (1-2 hours)
- Per schema change: 30 min - 1 hour (updating test assertions)

#### 5. Infrastructure Maintenance (30 min per version added)

When adding a new version (e.g., V3):

```
Step 1: Create /data-access-api/v3/ spec files        15 min
Step 2: Update SecurityConfig with /v3/** path        15 min
Step 3: Test spec endpoint accessibility              10 min
──────────────────────────────────────────────────────
Total: 40 min per new version
```

This is distinct from schema changes and happens less frequently (per version, not per feature)

---

## Total Cost of Current Approach

### Initial Investment
```
Completed work:          10 hours ✅
Example validation:      1-2 hours (still needed)
Consumer documentation: 1-2 hours (still needed)
──────────────────────────────────────
Subtotal:               12-14 hours
```

### Year 1 Ongoing (Assuming 12 Feature Additions)

```
Per-feature additions:  12 × 2.5h = 30 hours
Example maintenance:    12 × 0.5h = 6 hours
Test assertion updates: 12 × 0.5h = 6 hours
──────────────────────────────────────
Subtotal:               42 hours/year
```

**Total Year 1: 55-58 hours**

### Multi-Year Outlook

With ongoing development:
- **Year 1**: 55-58 hours (initial + feature work + 1 new version)
- **Year 2**: ~44 hours (1-2 new versions + ongoing features)
- **Year 3**: ~44 hours (1-2 new versions + ongoing features)
- **5-year total: ~220+ hours** of developer time
  - Feature additions: ~150 hours (12 features/year × 5 years × 2.5h = 150h)
  - Example maintenance: ~30 hours (6h/year × 5 years)
  - New version infrastructure: ~15-20 hours (estimated 4-5 versions × ~0.67h each)
  - Test assertion updates: ~30 hours (6h/year × 5 years)
  - Security/infrastructure: ~5-10 hours (miscellaneous updates)

---

## Why Examples Are Brittle in This Approach

### Root Cause

YAML examples are static files with no connection to:
- ❌ ApplicationContent model (no compile-time check)
- ❌ Type classes (ApplyApplication, etc.)
- ❌ Validation rules (JSR-303 annotations)
- ❌ Schema definitions

They are literally just JSON blobs someone wrote and stored in a file.

### Failure Modes

#### 1. Missing Required Field
```yaml
# Old example (before schema change)
v1_apply_example:
  value:
    id: "..."
    submittedAt: "..."
    # Field "reasonForApplication" doesn't exist yet

# Developer adds @NotNull reasonForApplication to ApplicationContent

# Now example is INVALID but nobody knows
# Spec still shows it in Swagger
# Consumer copies it → validation error
# Support team gets tickets: "Your example doesn't work!"
```

#### 2. Changed Field Type
```yaml
# Original
submittedAt: "2024-03-19T10:30:00Z"  # ISO-8601

# Developer changes format
submittedAt: "2024-03-19T10:30:00+00:00"  # ISO-8601 with timezone

# Example still shows old format
# Consumer copies it → format validation error
```

#### 3. Renamed Property
```yaml
# Old spec
applicant:
  firstName: "John"
  lastName: "Doe"

# Developer refactors to
applicantName:
  first: "John"
  last: "Doe"

# Example still shows "applicant" → doesn't match schema
# Consumer gets validation error
```

#### 4. Enum Value Changed
```yaml
# Old
status: "DRAFT"

# Developer changes enum values
status: "APPLICATION_DRAFT"

# Example still uses old value
# Valid in database, invalid in spec
```

### No Safety Mechanism

**The critical issue:** There is **no automated way to catch these** stale examples

**Current state:**
- ✅ Code changes will fail (JUnit catches it)
- ✅ Type mismatch will fail (compile-time check catches it)
- ❌ **Examples will NOT fail** (they're just static YAML files)
- ❌ No test validates examples against schema
- ❌ Examples ship as-is to documentation
- ❌ Consumers get broken examples when they copy them
- ❌ Support tickets result

---

## Production Risk Assessment

### If Committing This Branch As-Is

**Risk Level: MEDIUM-HIGH** ⚠️

| Risk | Likelihood | Impact | Severity |
|------|-----------|--------|----------|
| **Stale examples in docs** | High (inevitable) | Consumers fail to use API | Medium |
| **Test brittle on schema changes** | High (JSR-303 fragile) | Tests fail unpredictably | Medium |
| **Type class mismatch with schema** | Medium (synchronization drift) | Silent validation gaps | High |
| **Additional properties lost** | Medium (putAdditionalProperty mistakes) | Data corruption risk | High |
| **Maintenance burden increases** | High (2.5h/feature) | Developer velocity decreases | Medium |

### Mitigations That Would Help

1. **Add example validation tests** (-1 hour severity)
   - Manually validate examples against current schemas
   - Helps catch obvious stale examples
   - Still brittle and requires maintenance

2. **Establish example maintenance discipline** (-0.5 hour severity)
   - Documentation and code review checklists
   - Doesn't eliminate risk but increases awareness

3. **Plan for schema change testing** (-0.5 hour severity)
   - Allocate 30 min per schema change to manually verify examples
   - Update test assertions when validation changes

---

## When This Approach Makes Sense

This approach would be **justified** if:

- ✅ You expect **many versions** (V3, V4, V5+) with significant differences
- ✅ You want **discriminator-based routing** (selecting schema based on objectType field)
- ✅ You're willing to accept **2.5h per feature** maintenance burden
- ✅ Your team has **strong discipline** around example maintenance
- ✅ You have **automated tests** to validate examples against schemas

**Current state:** Manual example management with significant brittleness

---

## Assessment & Recommendations

### Current Status
- ✅ **Functional** — all tests pass
- ✅ **Specs well-organized** — versioning is clear
- ✅ **Discriminator implemented** — Swagger shows variants
- ❌ **Over-engineered** — type classes unnecessary
- ❌ **Examples brittle** — no validation, guaranteed to rot
- ❌ **High maintenance** — 2.5-3h per feature addition

### Before Production

**Current branch status:**
- ✅ Functional, all tests pass
- ✅ Specs well-organized by version
- ✅ Clear discriminator pattern in OpenAPI
- ❌ Examples not validated (guaranteed to rot)
- ❌ High maintenance burden (2.5h per feature)
- ❌ Validation assertions fragile (JSR-303 nested validation)

**Recommendations to address risks:**

1. **Add example validation tests** (1-2 hours)
   - Manually validate examples against schemas
   - Catches obvious stale examples
   - Still brittle but better than nothing

2. **Establish example maintenance discipline** (documentation)
   - Document that examples must be updated when schema changes
   - Code review checklist: "Examples updated?"
   - Doesn't prevent drift but increases awareness

3. **Plan for schema change testing** (30 min per change)
   - When modifying ApplicationContent or type classes
   - Manually verify example files still valid
   - Update test assertions when validation changes

4. **Track technical debt**
   - Note: 2.5h per feature is ongoing cost
   - Consider refactoring if this becomes pain point

---

## Summary

This assessment focuses solely on the current OpenAPI Discriminator approach currently implemented in branch `DSTEW-1171-Open_API-Discriminator-POC`:

**Strengths:**
- Clear versioning structure (v1, v2, common directories)
- Discriminator pattern well-documented in specs
- Specs copy automation reduces maintenance
- All tests passing

**Weaknesses:**
- Test data boilerplate (7 lines per test × 50 tests = 400+ extra lines)
- Type class synchronization burden (4 classes to maintain)
- Infrastructure complexity (Gradle task, Controller, Security config)
- Examples completely unvalidated (guaranteed to become stale)
- High per-feature maintenance (2.5-3 hours each time schema changes)
- Validation assertions fragile (JSR-303 nested validation brittle)
- Security configuration must be maintained per version (30 min per version)

**Production readiness:** Functional but risky without automated example validation

**Recommendation:** Address example brittleness before committing to production

