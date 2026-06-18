package uk.gov.justice.laa.dstew.access.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Registers versioned applicationContent JSON Schemas in OpenAPI. Rewrites relative schema
 * references to OpenAPI component references. Maps ApplicationCreateRequest.applicationContent to
 * the latest APPLY schema.
 */
@Component
public class ApplicationContentSchemaCustomizer implements OpenApiCustomizer {

  /**
   * Maps the filename portion of a JSON Schema {@code $ref} to its OpenAPI component name. Order
   * matters: entries are checked via {@link String#contains}, so more specific names should come
   * before shorter ones if there is any risk of overlap.
   */
  private static final Map<String, String> REF_TRANSLATIONS =
      Map.of(
          "Proceeding.json", "#/components/schemas/Proceeding",
          "ApplicationOffice.json", "#/components/schemas/ApplicationOffice",
          "LinkedApplication.json", "#/components/schemas/LinkedApplication",
          "Address.json", "#/components/schemas/Address",
          "Applicant.json", "#/components/schemas/Applicant");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void customise(OpenAPI openApi) {
    var components = openApi.getComponents();

    // Register common sub-schemas first so the versioned schemas can reference them.
    addSchemaFromClasspath(components, "Address", "schema/common/Address.json");
    addSchemaFromClasspath(components, "ApplicationOffice", "schema/common/ApplicationOffice.json");
    addSchemaFromClasspath(components, "LinkedApplication", "schema/common/LinkedApplication.json");
    addSchemaFromClasspath(components, "Proceeding", "schema/common/Proceeding.json");
    addSchemaFromClasspath(components, "Applicant", "schema/common/Applicant.json");

    // Register versioned application-content schemas.
    addSchemaFromClasspath(
        components, "ApplyApplicationContentV1", "schema/1/ApplyApplication.json");
    addSchemaFromClasspath(
        components, "ApplyApplicationContentV2", "schema/2/ApplyApplication.json");
    addSchemaFromClasspath(components, "CssApplicationContent", "schema/1/CssApplication.json");

    var schemas = components.getSchemas();
    if (schemas == null) {
      return;
    }

    Schema<?> appCreateRequest = schemas.get("ApplicationCreateRequest");
    if (appCreateRequest == null || appCreateRequest.getProperties() == null) {
      return;
    }

    Schema<?> contentRef = new Schema<>();
    contentRef.set$ref("#/components/schemas/ApplyApplicationContentV2");
    appCreateRequest.getProperties().put("applicationContent", contentRef);
  }

  private void addSchemaFromClasspath(
      io.swagger.v3.oas.models.Components components, String componentName, String classpathPath) {

    JsonNode root = readJsonNode(classpathPath);
    if (root == null) {
      return;
    }

    if (components.getSchemas() == null) {
      components.setSchemas(new LinkedHashMap<>());
    }
    components.getSchemas().put(componentName, convertNode(root));
  }

  private JsonNode readJsonNode(String classpathPath) {
    ClassPathResource resource = new ClassPathResource(classpathPath);
    if (!resource.exists()) {
      return null;
    }
    try (InputStream is = resource.getInputStream()) {
      return objectMapper.readTree(is);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Recursively converts a JSON Schema {@link JsonNode} into a swagger-core {@link Schema}.
   *
   * <p>Handles the subset of JSON Schema keywords actually used by the schemas in this project:
   * {@code type}, {@code format}, {@code description}, {@code $ref}, {@code properties}, {@code
   * required}, {@code items}, {@code oneOf}, {@code minItems}, and {@code additionalProperties}.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Schema<?> convertNode(JsonNode node) {
    Schema schema = new Schema<>();

    if (node.has("$ref")) {
      schema.set$ref(translateRef(node.get("$ref").asText()));
    }

    if (node.has("type")) {
      JsonNode typeNode = node.get("type");
      if (typeNode.isArray()) {
        for (JsonNode t : typeNode) {
          if (!"null".equals(t.asText())) {
            schema.setType(t.asText());
          }
        }
        schema.setNullable(true);
      } else {
        schema.setType(typeNode.asText());
      }
    }

    if (node.has("format")) {
      schema.setFormat(node.get("format").asText());
    }

    if (node.has("description")) {
      schema.setDescription(node.get("description").asText());
    }

    if (node.has("title")) {
      schema.setTitle(node.get("title").asText());
    }

    if (node.has("properties")) {
      Map<String, Schema> properties = new LinkedHashMap<>();
      node.get("properties")
          .properties()
          .forEach(e -> properties.put(e.getKey(), convertNode(e.getValue())));
      schema.setProperties(properties);
    }

    if (node.has("required")) {
      List<String> required = new ArrayList<>();
      node.get("required").forEach(n -> required.add(n.asText()));
      schema.setRequired(required);
    }

    if (node.has("items")) {
      schema.setItems(convertNode(node.get("items")));
    }

    if (node.has("oneOf")) {
      List<Schema> oneOf = new ArrayList<>();
      node.get("oneOf").forEach(n -> oneOf.add(convertNode(n)));
      schema.setOneOf(oneOf);
    }

    if (node.has("minItems")) {
      schema.setMinItems(node.get("minItems").asInt());
    }

    if (node.has("additionalProperties")) {
      JsonNode ap = node.get("additionalProperties");
      if (ap.isBoolean()) {
        schema.setAdditionalProperties(ap.asBoolean());
      } else {
        schema.setAdditionalProperties(convertNode(ap));
      }
    }

    return schema;
  }

  private String translateRef(String jsonSchemaRef) {
    for (Map.Entry<String, String> entry : REF_TRANSLATIONS.entrySet()) {
      if (jsonSchemaRef.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return jsonSchemaRef;
  }
}
