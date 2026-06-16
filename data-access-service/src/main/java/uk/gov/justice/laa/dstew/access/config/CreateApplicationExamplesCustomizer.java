package uk.gov.justice.laa.dstew.access.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Generates named Swagger UI examples for the {@code createApplication} operation directly from
 * the JSON Schema definition files. The {@code applicationContent} in each example is built by
 * introspecting the schema's properties, so renaming or adding a field in the schema file
 * automatically updates the Swagger example on next restart — no Java changes needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateApplicationExamplesCustomizer implements OperationCustomizer {

  private static final String CREATE_APPLICATION_OPERATION_ID = "createApplication";
  private static final String MEDIA_TYPE = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

  private final ObjectMapper objectMapper;

  /**
   * Each variant points to an actual JSON Schema definition file. The example shown in Swagger UI
   * is generated from that file's {@code properties}, so it always matches the live schema.
   */
  private static final List<ExampleVariant> VARIANTS = List.of(
      new ExampleVariant(
          "apply_v1",
          "APPLY — version 1 (id + submittedAt required)",
          "schema/1/ApplyApplication.json"
      ),
      new ExampleVariant(
          "apply_v2",
          "APPLY — version 2 (id, submittedAt, office, proceedings, applicant required)",
          "schema/2/ApplyApplication.json"
      ),
      new ExampleVariant(
          "css_v1",
          "CSS — version 1 (id, submittedAt, laaReference required)",
          "schema/1/CssApplication.json"
      )
  );

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    if (!CREATE_APPLICATION_OPERATION_ID.equals(operation.getOperationId())) {
      return operation;
    }

    Map<String, Example> examples = new LinkedHashMap<>();
    for (ExampleVariant variant : VARIANTS) {
      Map<String, Object> applicationContent = generateExampleFromSchema(variant.schemaPath());
      if (applicationContent != null) {
        Example example = new Example();
        example.setSummary(variant.summary());
        example.setValue(buildRequestWrapper(applicationContent));
        examples.put(variant.key(), example);
      }
    }

    if (examples.isEmpty()) {
      return operation;
    }

    RequestBody requestBody = operation.getRequestBody();
    if (requestBody == null) {
      requestBody = new RequestBody();
      operation.setRequestBody(requestBody);
    }
    Content content = requestBody.getContent();
    if (content == null) {
      content = new Content();
      requestBody.setContent(content);
    }
    MediaType mediaType = content.computeIfAbsent(MEDIA_TYPE, k -> {
      MediaType mt = new MediaType();
      mt.setSchema(new Schema<>().$ref("#/components/schemas/ApplicationCreateRequest"));
      return mt;
    });
    mediaType.setExamples(examples);
    return operation;
  }

  // ---------------------------------------------------------------------------
  // Schema-driven example generation
  // ---------------------------------------------------------------------------

  /**
   * Reads the JSON Schema file at {@code schemaPath} and generates a representative example object
   * by walking its {@code properties}. {@code $ref} entries are resolved relative to the schema's
   * own directory, so cross-file references like {@code ../common/Proceeding.json} work correctly.
   */
  private Map<String, Object> generateExampleFromSchema(String schemaPath) {
    JsonNode schema = readJsonNode(schemaPath);
    if (schema == null) {
      log.warn("Schema file not found on classpath: {}", schemaPath);
      return null;
    }
    String basePath = schemaPath.substring(0, schemaPath.lastIndexOf('/') + 1);
    return generateObjectExample(schema, basePath);
  }

  private Map<String, Object> generateObjectExample(JsonNode schema, String basePath) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (!schema.has("properties")) {
      return result;
    }
    schema.get("properties").properties()
        .forEach(e -> result.put(e.getKey(), generateValueExample(e.getValue(), basePath)));
    return result;
  }

  private Object generateValueExample(JsonNode propSchema, String basePath) {
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

    // oneOf: pick the first non-null branch
    if (propSchema.has("oneOf")) {
      for (JsonNode option : propSchema.get("oneOf")) {
        if ("null".equals(firstType(option))) {
          continue;
        }
        return generateValueExample(option, basePath);
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
        yield "example-string";
      }
      case "boolean" -> true;
      case "integer" -> 1;
      case "number" -> 1.0;
      case "array" -> {
        if (propSchema.has("items")) {
          Object item = generateValueExample(propSchema.get("items"), basePath);
          yield item != null ? List.of(item) : List.of();
        }
        yield List.of();
      }
      case "object" -> generateObjectExample(propSchema, basePath);
      default -> null;
    };
  }

  private String exampleStringForFormat(String format) {
    return switch (format) {
      case "uuid" -> "550e8400-e29b-41d4-a716-446655440000";
      case "date-time" -> "2024-03-21T09:00:00Z";
      case "date" -> "2024-03-21";
      default -> "example-string";
    };
  }

  /**
   * Returns the first non-null type string from a schema node's {@code type} field.
   */
  private String firstType(JsonNode schema) {
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
   * Resolves a relative JSON Schema {@code $ref} path against a base directory path.
   * Example: basePath={@code "schema/1/"}, ref={@code "../common/Proceeding.json"}
   * → {@code "schema/common/Proceeding.json"}
   */
  private String resolveRef(String basePath, String ref) {
    String[] parts = (basePath + ref).split("/");
    List<String> resolved = new ArrayList<>();
    for (String part : parts) {
      if ("..".equals(part)) {
        if (!resolved.isEmpty()) {
          resolved.remove(resolved.size() - 1);
        }
      } else if (!part.isEmpty() && !".".equals(part)) {
        resolved.add(part);
      }
    }
    return String.join("/", resolved);
  }

  /**
   * Wraps the schema-generated {@code applicationContent} in the outer
   * {@code ApplicationCreateRequest} envelope fields (status, laaReference, individuals).
   */
  private Map<String, Object> buildRequestWrapper(Map<String, Object> applicationContent) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("status", "APPLICATION_IN_PROGRESS");
    request.put("applicationContent", applicationContent);
    request.put("laaReference", "LAA-000-001");
    request.put("individuals", List.of(
        Map.of(
            "firstName", "Jane",
            "lastName", "Smith",
            "dateOfBirth", "1990-01-15",
            "type", "CLIENT",
            "details", Map.of("niNumber", "AB123456C")
        )
    ));
    return request;
  }

  private JsonNode readJsonNode(String classpathPath) {
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

  private record ExampleVariant(String key, String summary, String schemaPath) {
  }
}
