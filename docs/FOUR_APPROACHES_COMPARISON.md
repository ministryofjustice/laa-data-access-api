# Four Approaches Comparison: OpenAPI & Versioning Strategy

**Context:** Choosing how to handle versioned `applicationContent` with OpenAPI specs

---

## Overview Table

| Dimension | Main Branch | Discriminator (Current) | JsonSchema POC | JsonSchema + PACT |
|-----------|-----------|---------|---------|---------|
| **Versioning** | Monolithic spec | Versioned specs | Versioned schemas | Versioned schemas |
| **Test data** | Map (2 lines) | Typed objects (7 lines) | Map (2 lines) | Map (2 lines) |
| **Example validation** | None | Manual | JsonSchema | JsonSchema + PACT |
| **Infrastructure** | None | Gradle + Controller | Gradle + JsonSchema | Gradle + JsonSchema + PACT |
| **Type classes** | 1 | 4 | 1 | 1 |
| **Security config** | Unchanged | Grows per version | Unchanged | Unchanged |
| **Risk Level** | Low (no versioning) | Medium-High | Low | Very Low |

---

## Detailed Comparison

### 1. Main Branch (Monolithic)

**What it is:** Single monolithic OpenAPI spec, Map-based test data, no discriminator

#### ✅ Pros
- **Simple test code**: 2-3 lines per test setup
- **Minimal infrastructure**: No Gradle tasks, controllers, or security changes
- **Single type class**: ApplicationContent only
- **No versioning complexity**: Everything in one spec file
- **Low risk**: Simple = fewer things to break
- **Easy to understand**: New developers get it immediately

#### ❌ Cons
- **No explicit versioning**: v1, v2 buried in monolithic file
- **Scaling nightmare**: Adding V3 means editing large existing file
- **No discriminator support**: Clients can't see variant differences
- **No examples visible**: Documentation unclear
- **Hard to maintain versions**: Mixed versions in one file prone to errors
- **Future deprecation unclear**: How to mark endpoints as deprecated?

#### **Best For**
- API unlikely to version
- Simple, stable scope
- Team values minimalism

---

### 2. Discriminator Approach (Current Branch)

**What it is:** Versioned specs with discriminator, typed objects, manual example validation

#### ✅ Pros
- **Clear versioning**: v1/, v2/, common/ structure immediately obvious
- **Discriminator documented**: Swagger shows schema variants
- **Organized examples**: Examples grouped by version
- **Single-source specs**: Gradle copies to resources, automatic
- **Explicit versioning path**: Clear how to add V3, V4, etc.

#### ❌ Cons
- **Test boilerplate**: 7 lines per test (400+ extra lines across 50 tests)
- **Type class proliferation**: 4 classes to maintain in sync
- **Infrastructure required**: Gradle task, Controller, Security config
- **Security config grows**: Must add paths per new version
- **Brittle examples**: No validation, guaranteed to rot
- **High per-feature cost**: 2.5-3 hours per schema change
- **Fragile validation**: JSR-303 nested validation breaks easily
- **Risk: MEDIUM-HIGH**: Stale examples, lost properties, sync issues

#### **Best For**
- Multiple versions expected (V3+)
- Team has strong discipline
- Examples validated separately

---

### 3. JsonSchema POC (Standalone)

**What it is:** Versioned JSON schemas, Map-based test data, runtime validation

#### ✅ Pros
- **Auto-validated examples**: JsonSchema validates all examples at test time
- **Simple test code**: 2-3 lines per test setup
- **Single type class**: No proliferation
- **Runtime flexibility**: Schema version selected via parameter/header
- **Minimal test boilerplate**: No type conversion complexity
- **Version scaling**: Easy to add V3 (just drop JSON schema files)
- **Rich validation**: JSON Schema 2020-12 very powerful
- **Risk: LOW**: Validation catches issues immediately

#### ❌ Cons
- **Two validation systems**: Bean validation (envelope) + JsonSchema (content)
- **Swagger UI limitation**: Specs show generic object, not structured
- **Consumers must know about JsonSchema**: Not visible in OpenAPI
- **Dependency on networknt**: Adds library dependency
- **No consumer contract testing**: Nothing validates pre-beta
- **Examples still need examples**: Must be maintained (but validated)

#### **Best For**
- Clear separation of concerns (stable envelope, dynamic content)
- Automatic example validation important
- Rich validation rules needed

---

### 4. JsonSchema + PACT (Recommended Hybrid)

**What it is:** Versioned schemas with consumer contract testing for pre-beta validation

#### ✅ Pros
- **Triple validation layer**: JsonSchema (test) + PACT (pre-beta) + Consumer tests (production)
- **Auto-validated examples**: Caught immediately if stale
- **Simple test code**: 2-3 lines per test setup
- **Consumer contracts**: PACT ensures breaking changes caught before beta
- **Single type class**: No proliferation
- **Risk: VERY LOW**: Examples validated, contracts verified, consumers test
- **Production readiness**: Highest confidence before shipping
- **Version scaling**: Easy to add V3+ (just schemas)
- **Explicit consumer expectations**: PACT documents what consumers need

#### ❌ Cons
- **PACT implementation required**: Needs setup and maintenance
- **More moving parts**: JsonSchema + PACT + consumer tests
- **Swagger UI limitation**: Specs show generic object
- **Dependency coordination**: Must coordinate with consumers on PACT tests
- **Learning curve**: Team needs to understand PACT

#### **Best For**
- Production-ready API with external consumers
- Breaking changes must be caught early
- Team has capacity for PACT setup
- Beta testing before production release
- High reliability requirements

---

## Risk Assessment Matrix

| Risk Factor | Main | Discriminator | JsonSchema | JsonSchema+PACT |
|-------------|------|-------------|---------|---------|
| **Stale examples** | None (no examples) | HIGH ❌ | LOW ✅ | VERY LOW ✅ |
| **Breaking changes undetected** | HIGH ❌ | HIGH ❌ | MEDIUM ⚠️ | VERY LOW ✅ |
| **Validation errors in tests** | LOW ✅ | MEDIUM ⚠️ | LOW ✅ | VERY LOW ✅ |
| **Type safety losses** | MEDIUM ⚠️ | LOW ✅ | MEDIUM ⚠️ | MEDIUM ⚠️ |
| **Infrastructure breaks** | VERY LOW ✅ | MEDIUM ⚠️ | LOW ✅ | LOW ✅ |
| **Data corruption** | LOW ✅ | MEDIUM ⚠️ | LOW ✅ | LOW ✅ |
| **Scaling complexity** | HIGH ❌ | LOW ✅ | LOW ✅ | LOW ✅ |
| **Maintenance burden** | LOW ✅ | MEDIUM ⚠️ | LOW ✅ | LOW ✅ |

**Overall Risk Level:**
- Main: MEDIUM (no versioning handling)
- Discriminator: MEDIUM-HIGH (stale examples, complex)
- JsonSchema: LOW (auto-validated)
- JsonSchema+PACT: VERY LOW (multi-layer validation)

---

## Maintenance Per Feature Addition

### Main Branch
```
Step 1: Update ApplicationContent.java        15 min
Step 2: Tests automatically pick it up        0 min
Total: 15 min per feature
```

### Discriminator Approach
```
Step 1: Update ApplicationContent.java        15 min
Step 2: Update ApplyApplication              15 min
Step 3: Update ApplyApplication1             15 min
Step 4: Update DecideApplication             15 min
Step 5: Update generators                    30 min
Step 6: Update 50+ tests                     45 min
Step 7: Update spec examples                 30 min
Step 8: Fix if examples break                30 min
Step 9: Fix validation assertions            30 min
Total: 2.5-3 hours per feature
```

### JsonSchema POC
```
Step 1: Update ApplicationContent.java        15 min
Step 2: Update schema files (v1, v2, etc)    15 min
Step 3: Tests automatically work              0 min
Step 4: JsonSchema validates examples         0 min
Total: 30 min per feature
```

### JsonSchema + PACT
```
Step 1: Update ApplicationContent.java        15 min
Step 2: Update schema files                  15 min
Step 3: Update PACT tests                    15 min
Step 4: Consumer team updates their tests    TBD
Step 5: Tests automatically work              0 min
Total: 45 min per feature (+ consumer coordination)
```

---

## Adding New Version (V3)

### Main Branch
```
- Edit monolithic spec file (2-3 hours, error-prone)
- Risk: Accidental changes to v1, v2
- Deprecation unclear
Duration: 2-3 hours, HIGH RISK
```

### Discriminator Approach
```
- Create /data-access-api/v3/ spec files (15 min)
- Create ApplyApplicationV3 type class (30 min)
- Update discriminator mapping (15 min)
- Update SecurityConfig (15 min)
- Test spec endpoints (10 min)
Duration: 1.5 hours, MEDIUM RISK
```

### JsonSchema POC
```
- Create /data-access-api/v3/ spec files (15 min)
- Create /jsonschema/v3/ schema files (15 min)
- Add example validation tests (10 min)
- Test spec endpoints (10 min)
Duration: 50 min, LOW RISK
```

### JsonSchema + PACT
```
- Create /data-access-api/v3/ spec files (15 min)
- Create /jsonschema/v3/ schema files (15 min)
- Add PACT provider tests (30 min)
- Notify consumers to add PACT consumer tests (ongoing)
Duration: 1 hour + consumer coordination, LOW RISK
```

---

## Example Validation

### Main Branch
- ❌ No examples
- ❌ No validation possible

### Discriminator Approach
- ⚠️ Manual validation only
- ⚠️ Examples rot undetected
- ⚠️ Brittle (depends on JSR-303 annotations)

### JsonSchema POC
```java
@Test void example_v1_apply_simple_is_valid() {
    Map example = loadYamlExample("v1_apply_simple");
    JsonSchema schema = loadJsonSchema("apply-v1.json");
    schema.validate(example); // ✅ Fails if invalid
}
```
- ✅ Automatic validation
- ✅ Catches stale examples immediately
- ✅ Clear error messages

### JsonSchema + PACT
```
// JsonSchema catches missing fields
// PACT catches contract violations
// Consumer tests catch real-world issues
```
- ✅✅ Triple validation
- ✅ Stale examples caught in CI
- ✅ Breaking changes caught pre-beta

---

## Experience for API Consumers

### Main Branch
- ❌ No versioning hint in API
- ⚠️ Mix of v1/v2 endpoints unclear
- ❌ No examples

### Discriminator Approach
- ✅ Discriminator variant visible in Swagger
- ✅ Examples organized by version
- ⚠️ Examples may be stale (just discovered at runtime)
- ✅ Clear `objectType` field

### JsonSchema POC
- ✅ Generic object in Swagger (must read JsonSchema docs separately)
- ✅ Versioned examples in separate JSON schema files
- ✅ Examples guaranteed valid (validated by JsonSchema)
- ⚠️ Must understand JsonSchema `$ref` syntax

### JsonSchema + PACT
- ✅ Generic object in Swagger
- ✅ Versioned examples guaranteed valid
- ✅✅ Consumer contracts verified (PACT broker shows what's used)
- ✅ Clear what consumers actually need (from PACT tests)
- ✅ Early warning if you'd break them

---

## Integration Effort (Days)

| Task | Main | Discriminator | JsonSchema | JsonSchema+PACT |
|------|------|-------------|---------|---------|
| **Initial setup** | 0.5 day | 1.5 days | 1 day | 2 days |
| **Type classes** | 0 | 1 day | 0 | 0 |
| **Infrastructure** | 0 | 0.5 day | 0.5 day | 0.5 day |
| **Example validation** | 0 | 0.5 day | 0.25 day | 1 day |
| **Test updates** | 0.5 day | 1 day | 0.5 day | 0.5 day |
| **Consumer setup** | 0 | 0 | 0 | 2+ days |
| **TOTAL** | **1 day** | **4 days** | **2.25 days** | **6+ days** |

---

## Decision Matrix

### Choose Main Branch If:
- No versioning planned
- API stable forever
- Team prefers simplicity now
- Analytics: "We'll never add V2"

### Choose Discriminator If:
- Multiple versions definitely coming
- Team has strong discipline
- Manual example validation acceptable
- Don't want external consumer impact early

### Choose JsonSchema POC If:
- Multiple versions likely
- Automatic example validation important
- Simple test code priority
- No external consumer coordination needed
- Internal examples only

### Choose JsonSchema + PACT If:
- Production API with external consumers
- Breaking changes must be caught pre-beta
- Consumer contracts matter
- High reliability requirements
- Willing to invest in PACT setup

---

## Summary Recommendation

**For this project:**

| If... | Then Choose |
|-----|------------|
| Want to ship quickly with minimal risk | **JsonSchema + PACT** |
| Internal API, want simplicity | **JsonSchema POC** |
| Multiple versions coming, manual QA OK | **Discriminator** |
| Simple API, no versioning planned | **Main Branch** |
| Want to avoid over-engineering | **JsonSchema POC** |
| Consumer-facing, need contracts | **JsonSchema + PACT** |

**Current state:** JsonSchema POC exists and works → **JsonSchema + PACT is most aligned choice**

All approaches are viable. Choice depends on:
1. Expected versioning (clear V3+ → JsonSchema)
2. Consumer coordination willingness (yes → PACT)
3. Maintenance tolerance (low → JsonSchema)
4. Timeline (quick → JsonSchema POC or Main)

