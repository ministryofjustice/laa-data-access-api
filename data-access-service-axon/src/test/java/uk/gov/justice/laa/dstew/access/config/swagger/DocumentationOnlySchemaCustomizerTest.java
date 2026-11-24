package uk.gov.justice.laa.dstew.access.config.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

class DocumentationOnlySchemaCustomizerTest {

  private static final String FIXTURE = "openapi/documentation-only-schema-fixture.yaml";

  @Test
  void givenUnreferencedSchema_whenCustomise_thenCopiesItWithOneOfIntact() {
    var openApi = openApiWith("WidgetRequest");

    new DocumentationOnlySchemaCustomizer(FIXTURE).customise(openApi);

    Schema<?> validation = openApi.getComponents().getSchemas().get("WidgetValidationRequest");
    assertThat(validation).isNotNull();
    assertThat(validation.getOneOf()).hasSize(2);
    assertThat(validation.getOneOf().getFirst().getRequired()).containsExactly("radius");
    assertThat(validation.getOneOf().get(1).getNot().getRequired()).containsExactly("radius");
    Schema<?> part = validation.getProperties().get("parts").getItems();
    assertThat(part.getAnyOf().getFirst().getRequired()).containsExactly("size");
    assertThat(validation.getAllOf().getFirst().get$ref())
        .isEqualTo("#/components/schemas/WidgetRequest");
  }

  @Test
  void givenDocumentationSchemaDependencies_whenCustomise_thenCopiesOnlyMissingSchemas() {
    var openApi = openApiWith("WidgetRequest");
    Schema<?> runtimeRequest = openApi.getComponents().getSchemas().get("WidgetRequest");

    new DocumentationOnlySchemaCustomizer(FIXTURE).customise(openApi);

    assertThat(openApi.getComponents().getSchemas())
        .containsOnlyKeys("WidgetRequest", "WidgetValidationRequest", "WidgetDetail", "WidgetKind");
    assertThat(openApi.getComponents().getSchemas().get("WidgetRequest")).isSameAs(runtimeRequest);
  }

  @Test
  void givenSchemaOnlyReferencedByOperations_whenCustomise_thenDoesNotCopyIt() {
    var openApi = new OpenAPI();

    new DocumentationOnlySchemaCustomizer(FIXTURE).customise(openApi);

    assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("WidgetResponse");
  }

  @Test
  void givenMissingSpec_whenCustomise_thenLeavesDocumentUnchanged() {
    var openApi = new OpenAPI();

    new DocumentationOnlySchemaCustomizer("openapi/missing.yaml").customise(openApi);

    assertThat(openApi.getComponents()).isNull();
  }

  private static OpenAPI openApiWith(String schemaName) {
    return new OpenAPI()
        .components(new Components().addSchemas(schemaName, new Schema<>().type("object")));
  }
}
