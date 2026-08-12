package uk.gov.justice.laa.dstew.access.config.swagger;

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
  private static final List<Map.Entry<String, String>> REF_TRANSLATIONS =
      List.of(
          Map.entry("Proceeding.json", "#/components/schemas/Proceeding"),
          Map.entry("Provider.json", "#/components/schemas/Provider"),
          Map.entry("Client.json", "#/components/schemas/Client"),
          Map.entry("Opponent.json", "#/components/schemas/Opponent"),
          Map.entry("ScopeLimitation.json", "#/components/schemas/ScopeLimitation"),
          Map.entry("Child.json", "#/components/schemas/Child"),
          Map.entry("Address.json", "#/components/schemas/Address"));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void customise(OpenAPI openApi) {
    var components = openApi.getComponents();

    // Register common sub-schemas referenced by the application content schema.
    addSchemaFromClasspath(components, "Address", "schema/common/Address.json");
    addSchemaFromClasspath(components, "Proceeding", "schema/common/Proceeding.json");
    addSchemaFromClasspath(components, "Provider", "schema/common/Provider.json");
    addSchemaFromClasspath(components, "Client", "schema/common/Client.json");
    addSchemaFromClasspath(components, "Opponent", "schema/common/Opponent.json");
    addSchemaFromClasspath(components, "ScopeLimitation", "schema/common/ScopeLimitation.json");
    addSchemaFromClasspath(components, "Child", "schema/common/Child.json");

    // Register the application content schema.
    addSchemaFromClasspath(
        components, "ApplyApplicationContentV1", "schema/1/ApplyApplication.json");

    var schemas = components.getSchemas();
    if (schemas == null) {
      return;
    }

    Schema<?> appCreateRequest = schemas.get("ApplicationCreateRequest");
    if (appCreateRequest == null || appCreateRequest.getProperties() == null) {
      return;
    }

    // Point applicationContent to the application content schema in Swagger UI
    Schema<?> contentSchema = new Schema<>();
    contentSchema.set$ref("#/components/schemas/ApplyApplicationContentV1");
    contentSchema.setDescription("Application content conforming to the versioned schema");
    appCreateRequest.getProperties().put("applicationContent", contentSchema);
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
    for (Map.Entry<String, String> entry : REF_TRANSLATIONS) {
      if (jsonSchemaRef.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return jsonSchemaRef;
  }
}
