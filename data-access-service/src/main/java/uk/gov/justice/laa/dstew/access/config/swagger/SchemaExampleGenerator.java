package uk.gov.justice.laa.dstew.access.config.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Utility component for generating example objects from JSON Schema files. Intended for use by
 * {@link org.springdoc.core.customizers.OperationCustomizer} implementations that want to populate
 * Swagger UI examples directly from schema definitions, so the examples always reflect the live
 * schema without requiring manual updates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaExampleGenerator {

  /**
   * Well-known field name → example value mappings (case-insensitive lookup). Customisers that need
   * domain-specific overrides should consult this map when building their wrapper objects.
   */
  static final Map<String, String> FIELD_EXAMPLES =
      Map.of(
          "submittedat", "2024-03-21T09:00:00Z",
          "id", "550e8400-e29b-41d4-a716-446655440000",
          "status", "APPLICATION_IN_PROGRESS",
          "categoryoflaw", "Family",
          "mattertype", "SPECIAL_CHILDREN_ACT",
          "description", "Proceeding description",
          "code", "1L382A",
          "officecode", "1L382A",
          "office", "1L382A",
          "laareference", "LAA-000-001");

  private final ObjectMapper objectMapper;

  /**
   * Reads the JSON Schema file at {@code schemaPath} and generates a representative example object
   * by walking its {@code properties}.
   *
   * @param schemaPath classpath-relative path, e.g. {@code "schema/1/ApplyApplication.json"}
   * @return a {@code Map<String, Object>} of example field values, or {@code null} if the schema
   *     file cannot be found or read
   */
  public Map<String, Object> generateExampleFromSchema(String schemaPath) {
    JsonNode schema = readJsonNode(schemaPath);
    if (schema == null) {
      log.warn("Schema file not found on classpath: {}", schemaPath);
      return null;
    }
    String basePath = schemaPath.substring(0, schemaPath.lastIndexOf('/') + 1);
    return generateObjectExample(schema, basePath);
  }

  Map<String, Object> generateObjectExample(JsonNode schema, String basePath) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (!schema.has("properties")) {
      return result;
    }
    schema
        .get("properties")
        .properties()
        .forEach(
            e -> result.put(e.getKey(), generateValueExample(e.getKey(), e.getValue(), basePath)));
    return result;
  }

  Object generateValueExample(String fieldName, JsonNode propSchema, String basePath) {
    // Resolve $ref to another schema file
    if (propSchema.has("$ref")) {
      String resolvedPath = resolveRef(basePath, propSchema.get("$ref").asText());
      JsonNode refSchema = readJsonNode(resolvedPath);
      if (refSchema != null) {
        String refBase = resolvedPath.substring(0, resolvedPath.lastIndexOf('/') + 1);
        return generateObjectExample(refSchema, refBase);
      }
      return null;
    }

    // oneOf: pick the first non-null branch, preserving field name for value generation
    if (propSchema.has("oneOf")) {
      for (JsonNode option : propSchema.get("oneOf")) {
        if ("null".equals(firstType(option))) {
          continue;
        }
        return generateValueExample(fieldName, option, basePath);
      }
      return null;
    }

    String type = firstType(propSchema);
    if (type == null) {
      return null;
    }

    return switch (type) {
      case "string" -> {
        if (propSchema.has("format")) {
          yield exampleStringForFormat(propSchema.get("format").asText());
        }
        yield exampleStringForFieldName(fieldName);
      }
      case "boolean" -> true;
      case "integer" -> 1;
      case "number" -> 1.0;
      case "array" -> {
        if (propSchema.has("items")) {
          Object item = generateValueExample(null, propSchema.get("items"), basePath);
          yield item != null ? List.of(item) : List.of();
        }
        yield List.of();
      }
      case "object" -> generateObjectExample(propSchema, basePath);
      default -> null;
    };
  }

  String exampleStringForFormat(String format) {
    return switch (format) {
      case "uuid" -> "550e8400-e29b-41d4-a716-446655440000";
      case "date-time" -> "2024-03-21T09:00:00Z";
      case "date" -> "2024-03-21";
      case "email" -> "test@test.com";
      default -> "example-string";
    };
  }

  /**
   * Returns a realistic example string value based on the field name, using the same values as the
   * test data generators. Falls back to {@code "example-string"} for unrecognised names.
   */
  String exampleStringForFieldName(String fieldName) {
    if (fieldName == null) {
      return "example-string";
    }
    return FIELD_EXAMPLES.getOrDefault(fieldName.toLowerCase(), "example-string");
  }

  /** Returns the first non-null type string from a schema node's {@code type} field. */
  String firstType(JsonNode schema) {
    if (!schema.has("type")) {
      return null;
    }
    JsonNode typeNode = schema.get("type");
    if (typeNode.isTextual()) {
      return typeNode.asText();
    }
    if (typeNode.isArray()) {
      for (JsonNode t : typeNode) {
        if (!"null".equals(t.asText())) {
          return t.asText();
        }
      }
    }
    return null;
  }

  /**
   * Resolves a relative JSON Schema {@code $ref} path against a base directory path. Example:
   * basePath={@code "schema/1/"}, ref={@code "../common/Proceeding.json"} → {@code
   * "schema/common/Proceeding.json"}
   */
  String resolveRef(String basePath, String ref) {
    String[] parts = (basePath + ref).split("/");
    List<String> resolved = new ArrayList<>();
    for (String part : parts) {
      if ("..".equals(part)) {
        if (!resolved.isEmpty()) {
          resolved.removeLast();
        }
      } else if (!part.isEmpty() && !".".equals(part)) {
        resolved.add(part);
      }
    }
    return String.join("/", resolved);
  }

  JsonNode readJsonNode(String classpathPath) {
    ClassPathResource resource = new ClassPathResource(classpathPath);
    if (!resource.exists()) {
      return null;
    }
    try (InputStream is = resource.getInputStream()) {
      return objectMapper.readTree(is);
    } catch (IOException e) {
      log.error("Failed to read schema file '{}': {}", classpathPath, e.getMessage(), e);
      return null;
    }
  }
}
