
# JSON Schema Validation in Spring with Versioned Schemas

## How it works

Your schemas live in versioned directories (`schema/1/`, `schema/2/`, etc.). A validator loads the correct schema based on a version parameter passed into the endpoint, validates the raw JSON payload against it, and throws a `ValidationException` (which your `GlobalExceptionHandler` already handles) if it fails.

The best library for JSON Schema 2020-12 (which your schemas use) is **[networknt/json-schema-validator](https://github.com/networknt/json-schema-validator)** — it's the most actively maintained Java library, supports `$ref` across files, and works natively with Jackson `JsonNode`.

## The flow

```
Controller (receives schemaVersion param)
    → ApplicationService.createApplication(req, schemaVersion)
        → JsonSchemaValidator.validate(req.getApplicationContent(), schemaVersion)
            → loads schema/{{version}}/ApplyApplication.json
            → validates the raw Map<String, Object> against it
            → throws ValidationException on failure
        → PayloadValidationService.convertAndValidate(...)  // existing bean validation
```

## Implementation steps

### 1. Add the dependency

```groovy
// build.gradle
implementation 'com.networknt:json-schema-validator:1.5.7'
```

### 2. JsonSchemaValidator

The validator loads schemas from the classpath at `schema/{version}/`, using Jackson to convert the `Map<String, Object>` (the `applicationContent`) into a `JsonNode`, then validates it against the resolved schema. The `$ref`s between your files (e.g. `ApplyApplication.json → Proceeding.json`) are resolved automatically because they share the same classpath directory.

```java
@Component
public class JsonSchemaValidator {

  private final ObjectMapper objectMapper;

  public JsonSchemaValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Validate a payload against a named schema at the given version.
   *
   * @param payload       the object to validate (e.g. Map from applicationContent)
   * @param schemaName    the schema filename, e.g. "ApplyApplication.json"
   * @param schemaVersion the version directory, e.g. 1 → loads from schema/1/
   * @throws ValidationException if the payload does not conform to the schema
   */
  public void validate(Object payload, String schemaName, int schemaVersion) {
    JsonNode jsonNode = objectMapper.valueToTree(payload);

    // Build the classpath URI so $ref resolution works across files
    String schemaPath = "schema/" + schemaVersion + "/" + schemaName;
    SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();

    JsonSchema schema = JsonSchemaFactory
        .getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(SchemaLocation.of("classpath:" + schemaPath), config);

    Set<ValidationMessage> errors = schema.validate(jsonNode);

    if (!errors.isEmpty()) {
      List<String> messages = errors.stream()
          .map(ValidationMessage::getMessage)
          .toList();
      throw new ValidationException(messages);
    }
  }
}
```

### 3. Wire it into `ApplicationService`

The `schemaVersion` gets passed down from the controller. You already store it on the entity with `entity.setSchemaVersion(applicationVersion)` — now it comes from the caller instead of being hardcoded to `1`.

```java
// In createApplication:
jsonSchemaValidator.validate(
    req.getApplicationContent(), "ApplyApplication.json", schemaVersion
);
// ... then continue with existing convertAndValidate / entity mapping
entity.setSchemaVersion(schemaVersion);
```

### 4. Pass the version from the endpoint

Add a `schemaVersion` request parameter (or header, or path variable) to your OpenAPI spec / controller so the caller chooses which schema version to validate against:

```java
// Controller
public ResponseEntity<Void> createApplication(
    @NotNull ServiceName serviceName,
    @Valid ApplicationCreateRequest applicationCreateReq,
    @RequestParam(defaultValue = "1") int schemaVersion) {

  UUID id = service.createApplication(applicationCreateReq, schemaVersion);
  // ...
}
```

### 5. Add new schema versions

When the schema evolves, create a `schema/2/` directory with the updated JSON schema files. Callers that still send `schemaVersion=1` continue to be validated against `schema/1/`. New callers use `schemaVersion=2`.

```
resources/
  schema/
    1/
      ApplyApplication.json      ← current
      Proceeding.json
      ApplicationOffice.json
      ...
    2/
      ApplyApplication.json      ← evolved schema
      Proceeding.json            ← or $ref to ../1/ if unchanged
      ...
```

### Key points

- **`$ref` resolution** — networknt resolves `"$ref": "Proceeding.json"` relative to the schema's own URI, so because they're in the same classpath directory it just works.
- **Two layers of validation** — JSON Schema validates the *structure* of the raw payload; Bean Validation (`PayloadValidationService.convertAndValidate`) validates the *typed POJO* after conversion. Both are useful.
- **Schema version on the entity** — you already store `schemaVersion` on the entity, so on *read* you can deserialise using the correct schema/model version too.
