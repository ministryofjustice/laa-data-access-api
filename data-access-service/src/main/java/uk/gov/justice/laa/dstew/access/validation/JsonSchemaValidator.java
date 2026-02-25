package uk.gov.justice.laa.dstew.access.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates payloads against versioned JSON Schemas loaded from the classpath.
 * Schemas are expected at {@code schema/{version}/{schemaName}}, e.g.
 * {@code schema/1/ApplyApplication.json}.
 */
@Component
@RequiredArgsConstructor
public class JsonSchemaValidator {

  private final ObjectMapper objectMapper;

  /**
   * Validate a payload against a named schema at the given version.
   *
   * @param payload       the object to validate (e.g. {@code Map<String, Object>})
   * @param schemaName    the schema filename, e.g. {@code "ApplyApplication.json"}
   * @param schemaVersion the version directory, e.g. {@code 1} loads from {@code schema/1/}
   * @throws ValidationException if the payload does not conform to the schema
   */
  public void validate(Object payload, String schemaName, int schemaVersion) {
    JsonNode jsonNode = objectMapper.valueToTree(payload);

    String schemaPath = "schema/" + schemaVersion + "/" + schemaName;
    SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();
    JsonSchema schema;
    try {
      // Check schema exists and is valid
      schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
          .getSchema(SchemaLocation.of("classpath:" + schemaPath), config);
    } catch (Exception e) {
      throw new ValidationException("Failed to load JSON Schema: " + e.getMessage(),
          List.of("Invalid schema version: " + schemaVersion));
    }

    Set<ValidationMessage> errors = schema.validate(jsonNode);

    if (!errors.isEmpty()) {
      List<String> messages = errors.stream()
          .map(ValidationMessage::getMessage)
          .toList();
      throw new ValidationException(messages);
    }
  }
}
