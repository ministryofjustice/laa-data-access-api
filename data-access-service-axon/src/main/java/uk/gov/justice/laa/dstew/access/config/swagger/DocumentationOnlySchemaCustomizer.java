package uk.gov.justice.laa.dstew.access.config.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Publishes documentation-only schemas from the bundled source specification. springdoc derives the
 * runtime document from the generated code, so it omits schemas that no operation references (for
 * example, schemas documenting conditional validation). This copies every such schema, and any
 * schema it references that the runtime document lacks, exactly as written in the source spec.
 */
@Slf4j
@Component
public class DocumentationOnlySchemaCustomizer implements OpenApiCustomizer {

  static final String BUNDLED_SPEC_LOCATION = "openapi/openapi.yaml";
  private static final String COMPONENTS_REF_PREFIX = "#/components";
  private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

  private final String specLocation;

  public DocumentationOnlySchemaCustomizer() {
    this(BUNDLED_SPEC_LOCATION);
  }

  DocumentationOnlySchemaCustomizer(String specLocation) {
    this.specLocation = specLocation;
  }

  @Override
  public void customise(OpenAPI openApi) {
    JsonNode spec = readSpec();
    if (spec == null) {
      return;
    }
    JsonNode specComponents = spec.path("components");
    JsonNode specSchemas = specComponents.path("schemas");
    Set<String> referencedByOperations = schemasReachableFrom(spec.path("paths"), specComponents);

    if (openApi.getComponents() == null) {
      openApi.setComponents(new Components());
    }
    Components components = openApi.getComponents();

    var pending = new ArrayDeque<String>();
    specSchemas
        .fieldNames()
        .forEachRemaining(
            name -> {
              if (!referencedByOperations.contains(name)) {
                pending.add(name);
              }
            });

    var visited = new HashSet<String>();
    while (!pending.isEmpty()) {
      String name = pending.poll();
      JsonNode schemaNode = specSchemas.get(name);
      if (!visited.add(name) || schemaNode == null || hasSchema(components, name)) {
        continue;
      }
      components.addSchemas(name, toSchema(schemaNode));
      pending.addAll(schemaNames(refsIn(schemaNode)));
    }
  }

  private static Schema<?> toSchema(JsonNode schemaNode) {
    Schema<?> schema = Json31.mapper().convertValue(schemaNode, Schema.class);
    restoreRequired(schemaNode, schema);
    return schema;
  }

  /**
   * Schema.setRequired, used during deserialization, drops names missing from the sibling
   * properties, which strips conditional rules such as {@code required: [radius]}.
   */
  private static void restoreRequired(JsonNode node, Schema<?> schema) {
    if (schema == null) {
      return;
    }
    JsonNode required = node.get("required");
    if (required != null) {
      var names = new ArrayList<String>();
      required.forEach(name -> names.add(name.asText()));
      schema.required(names);
    }
    if (schema.getProperties() != null) {
      schema
          .getProperties()
          .forEach(
              (property, child) -> restoreRequired(node.path("properties").get(property), child));
    }
    restoreRequired(node.get("items"), schema.getItems());
    restoreRequired(node.get("not"), schema.getNot());
    restoreRequired(node.get("allOf"), schema.getAllOf());
    restoreRequired(node.get("anyOf"), schema.getAnyOf());
    restoreRequired(node.get("oneOf"), schema.getOneOf());
  }

  private static void restoreRequired(JsonNode nodes, List<Schema> schemas) {
    if (schemas == null) {
      return;
    }
    for (int index = 0; index < schemas.size(); index++) {
      restoreRequired(nodes.get(index), schemas.get(index));
    }
  }

  private JsonNode readSpec() {
    var resource = new ClassPathResource(specLocation);
    if (!resource.exists()) {
      log.warn("Bundled OpenAPI specification {} not found on the classpath", specLocation);
      return null;
    }
    try (var inputStream = resource.getInputStream()) {
      return Yaml31.mapper().readTree(inputStream);
    } catch (IOException exception) {
      log.warn("Unable to read bundled OpenAPI specification {}", specLocation, exception);
      return null;
    }
  }

  private static boolean hasSchema(Components components, String name) {
    return components.getSchemas() != null && components.getSchemas().containsKey(name);
  }

  private static Set<String> schemasReachableFrom(JsonNode paths, JsonNode specComponents) {
    var visitedRefs = new HashSet<String>();
    var pending = new ArrayDeque<>(refsIn(paths));
    while (!pending.isEmpty()) {
      String ref = pending.poll();
      if (!ref.startsWith(COMPONENTS_REF_PREFIX + "/") || !visitedRefs.add(ref)) {
        continue;
      }
      pending.addAll(refsIn(specComponents.at(ref.substring(COMPONENTS_REF_PREFIX.length()))));
    }
    return schemaNames(visitedRefs);
  }

  private static Set<String> schemaNames(Iterable<String> refs) {
    var names = new HashSet<String>();
    refs.forEach(
        ref -> {
          if (ref.startsWith(SCHEMA_REF_PREFIX)) {
            names.add(ref.substring(SCHEMA_REF_PREFIX.length()));
          }
        });
    return names;
  }

  private static List<String> refsIn(JsonNode node) {
    var refs = new ArrayList<String>();
    collectRefs(node, refs);
    return refs;
  }

  private static void collectRefs(JsonNode node, List<String> refs) {
    if (node.isObject()) {
      JsonNode ref = node.get("$ref");
      if (ref != null && ref.isTextual()) {
        refs.add(ref.asText());
      }
    }
    if (node.isContainerNode()) {
      node.forEach(child -> collectRefs(child, refs));
    }
  }
}
