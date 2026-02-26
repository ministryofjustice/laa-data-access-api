# Schema Validation Approaches: OpenAPI vs JSON Schema vs Hybrid

## Context

When building APIs that accept versioned or evolving payloads, there are several approaches to validating request content. This document compares three approaches in the context of a Spring Boot application, using the `applicationContent` field in `ApplicationCreateRequest` as the example.

The `applicationContent` field is a semi-structured payload (`Map<String, Object>`) whose shape can change between versions. The API needs to validate this payload against the correct schema version at runtime, selected via the `X-Schema-Version` header.

---

## Approach 1: OpenAPI Only

Each schema version is defined as a separate schema within the OpenAPI specification. Validation is handled entirely through generated code and bean validation annotations.

### How it works

- Define `ApplicationContentV1`, `ApplicationContentV2`, etc. as separate schemas in the OpenAPI spec.
- Create separate request schemas (`ApplicationCreateRequestV1`, `ApplicationCreateRequestV2`) that reference the corresponding content schema.
- Either create separate endpoints per version or use an OpenAPI `discriminator` to select the correct schema.

```yaml
# Separate schemas per version
ApplicationContentV1:
  type: object
  required: [id, submittedAt]
  properties:
    id: { type: string, format: uuid }
    submittedAt: { type: string }

ApplicationContentV2:
  type: object
  required: [id, submittedAt, newRequiredField]
  properties:
    id: { type: string, format: uuid }
    submittedAt: { type: string }
    newRequiredField: { type: string }
```

### Pros

- **Single source of truth** — everything lives in the OpenAPI spec.
- **Generated validation** — bean validation annotations (`@NotNull`, `@Valid`, `@Size`) are produced automatically by the code generator.
- **Typed classes** — each version gets its own generated Java class, giving compile-time type safety.
- **Tooling support** — Swagger UI, client SDKs, and documentation all reflect the full structure automatically.

### Cons

- **Heavy duplication** — every version requires a full copy of the request schema and all nested types (`Proceeding`, `ApplicationOffice`, `LinkedApplication`, `Individual`, etc.). Even unchanged fields must be repeated.
- **No runtime selection** — OpenAPI validation is static (compile-time). There is no native mechanism to say "validate against schema V1 or V2 based on a request header." You must either split into multiple endpoints or use a `discriminator`.
- **Endpoint sprawl** — separate endpoints per version (`/v1/applications`, `/v2/applications`) increases the API surface area and complicates routing.
- **Discriminator limitations** — OpenAPI's `oneOf`/`discriminator` has inconsistent support across code generators and client tooling. Selecting a schema based on a *header* value (rather than a field in the body) is not natively supported.
- **Regeneration churn** — every schema change requires regenerating API classes, updating controller signatures, and potentially updating all consumers.
- **`applicationContent` must be typed** — the field can no longer be `Map<String, Object>`; each version needs a distinct class, making the data layer more complex.

---

## Approach 2: JSON Schema Only (No OpenAPI Validation)

All validation is performed at runtime using JSON Schema files, with no structural validation from OpenAPI or bean validation.

### How it works

- The OpenAPI spec defines `applicationContent` (and potentially the entire request) as a generic `object` with no detailed structure.
- Versioned JSON Schema files live on the classpath (e.g. `schema/1/ApplicationCreateRequest.json`, `schema/2/ApplicationCreateRequest.json`).
- A `JsonSchemaValidator` component loads the appropriate schema at runtime based on the `X-Schema-Version` header and validates the raw JSON payload.
- No code generation is needed for content models.

```
resources/
  schema/
    1/
      ApplicationCreateRequest.json
      ApplyApplication.json
      Proceeding.json
      ...
    2/
      ApplicationCreateRequest.json   ← evolved schema
      ApplyApplication.json
      ...
```

### Pros

- **Full runtime flexibility** — schema version is selected dynamically based on request headers, query parameters, or any other runtime value.
- **Minimal duplication** — only the changed schema files need to exist in a new version directory. Unchanged schemas can be identical copies or referenced via `$ref`.
- **No code regeneration** — adding a new version is just adding JSON files; no Java classes or controller signatures change.
- **Rich validation** — JSON Schema 2020-12 supports `$ref` across files, `oneOf`, `pattern`, `format`, conditional schemas (`if`/`then`/`else`), and many features beyond what OpenAPI schemas offer.
- **Decoupled from API contract** — the content schema can evolve independently of the HTTP contract.

### Cons

- **No compile-time safety** — the payload is a `Map<String, Object>` throughout. Typos, missing fields, and type mismatches are only caught at runtime.
- **No generated documentation** — Swagger UI and generated client SDKs won't show the structure of `applicationContent`. Consumers must refer to the JSON Schema files separately.
- **No generated classes** — developers work with raw maps or must manually define POJOs.
- **Two validation systems** — if you still use bean validation for other parts of the request, you have JSON Schema for some fields and bean validation for others, which can be confusing.
- **Testing burden** — without compile-time types, more runtime tests are needed to catch structural issues.

---

## Approach 3: Hybrid — OpenAPI + JSON Schema (Current Approach)

OpenAPI defines the stable outer request structure and generates typed classes with bean validation. JSON Schema validates the dynamic inner `applicationContent` at runtime.

### How it works

- The OpenAPI spec defines the full `ApplicationCreateRequest` with typed fields: `status` (enum), `laaReference` (string), `individuals` (array of `Individual`), and `applicationContent` (generic `object` / `Map<String, Object>`).
- Code generation produces `ApplicationCreateRequest.java`, `Individual.java`, `ApplicationStatus.java`, etc. with `@NotNull`, `@Valid`, `@Size` annotations.
- Versioned JSON Schema files define the structure of `applicationContent` (e.g. `schema/1/ApplyApplication.json`).
- At runtime, `JsonSchemaValidator` validates the `applicationContent` map against the correct schema version selected by the `X-Schema-Version` header.
- After schema validation, `PayloadValidationService` converts the map into a typed `ApplicationContent` POJO and runs bean validation on it.

```
Request arrives
    │
    ├─ Bean validation (@Valid, @NotNull, @Size)       ← OpenAPI-generated
    │   validates: status, laaReference, individuals
    │
    ├─ JsonSchemaValidator.validate()                  ← JSON Schema (networknt)
    │   validates: applicationContent structure
    │   against schema/{version}/ApplyApplication.json
    │
    └─ PayloadValidationService.convertAndValidate()   ← Bean validation
        converts Map → ApplicationContent POJO
        validates typed POJO constraints
```

### Pros

- **Best of both worlds** — the stable envelope gets compile-time safety and generated documentation; the dynamic content gets runtime flexibility and versioning.
- **Single endpoint** — one `POST /api/v0/applications` endpoint with an `X-Schema-Version` header. No endpoint sprawl.
- **Minimal duplication** — adding a version means adding JSON Schema files in a new `schema/{version}/` directory. The OpenAPI spec, generated classes, and controller signatures don't change.
- **Independent evolution** — the content schema can change (new required fields, renamed properties, stricter validation) without affecting the API contract or requiring code regeneration.
- **Rich inner validation** — JSON Schema 2020-12 provides powerful validation features (`$ref`, `pattern`, `if/then/else`, `additionalProperties`) that go beyond what OpenAPI schemas support.
- **Good documentation** — the outer request is fully documented in Swagger UI. The inner content schemas are self-describing JSON Schema files that can be published separately.
- **Backwards compatible by default** — old consumers keep sending `X-Schema-Version: 1` and are validated against the V1 schema. New consumers opt into V2.

### Cons

- **Two validation mechanisms** — developers need to understand both bean validation (for the envelope) and JSON Schema (for the content). This adds conceptual overhead.
- **`applicationContent` is untyped in OpenAPI** — Swagger UI shows it as a generic object. Consumers must refer to the JSON Schema files to understand the expected structure.
- **Runtime-only content validation** — structural issues in `applicationContent` are caught at runtime, not compile time. Requires good test coverage.
- **Dependency on networknt** — adds `com.networknt:json-schema-validator` as a runtime dependency.

---

## Comparison Summary

| Concern                        | OpenAPI Only         | JSON Schema Only     | Hybrid (Current)           |
|--------------------------------|----------------------|----------------------|----------------------------|
| Compile-time type safety       | ✅ Full              | ❌ None              | ✅ Envelope only           |
| Runtime version selection      | ❌ Not supported     | ✅ Full              | ✅ For content             |
| Schema duplication per version | ❌ Heavy             | ✅ Minimal           | ✅ Minimal                 |
| Code regeneration on change    | ❌ Required          | ✅ Not needed        | ✅ Not needed for content  |
| Swagger UI documentation       | ✅ Full              | ❌ Generic object    | ⚠️ Envelope only          |
| Endpoint count                 | ❌ Multiple          | ✅ Single            | ✅ Single                  |
| Validation richness            | ⚠️ Limited          | ✅ Full JSON Schema  | ✅ Full JSON Schema        |
| Implementation complexity      | ⚠️ Discriminators   | ✅ Simple            | ⚠️ Two systems            |
| Adding a new version           | ❌ Spec + codegen    | ✅ Add JSON files    | ✅ Add JSON files          |

---

## Recommendation

The **hybrid approach** is the most practical choice for this project because:

1. **`applicationContent` is inherently dynamic** — it's stored as JSONB and its structure varies by version. Forcing it into a static OpenAPI schema would be fighting the design.
2. **The envelope is stable** — `status`, `laaReference`, and `individuals` change infrequently and benefit from compile-time types and generated validation.
3. **Version flexibility is a requirement** — callers need to choose which schema version to validate against at runtime, which OpenAPI cannot support natively.
4. **Minimal maintenance** — new versions only require dropping JSON Schema files into a new directory, with no spec changes, code regeneration, or controller updates.
