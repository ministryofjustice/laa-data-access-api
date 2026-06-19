package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationContentSchemaCustomizerTest {

  private final ApplicationContentSchemaCustomizer customizer =
      new ApplicationContentSchemaCustomizer();

  @Test
  void givenEmptyComponents_whenCustomise_thenRegistersAllExpectedSchemaComponents() {
    // given
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then
    Map<String, Schema> schemas = openApi.getComponents().getSchemas();
    assertThat(schemas)
        .containsKeys(
            "Address",
            "ApplicationOffice",
            "LinkedApplication",
            "Proceeding",
            "Applicant",
            "ApplyApplicationContentV1",
            "ApplyApplicationContentV2",
            "CssApplicationContent");
  }

  @Test
  void
      givenSchemaWithRequiredKeyword_whenCustomise_thenRequiredKeywordIsConvertedToSchemaRequiredList() {
    // given
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then
    Schema<?> schema = openApi.getComponents().getSchemas().get("ApplyApplicationContentV1");
    assertThat(schema).isNotNull();
    assertThat(schema.getRequired()).containsExactlyInAnyOrder("id", "submittedAt");
  }

  @Test
  void
      givenSchemaWithMinItemsConstraint_whenCustomise_thenMinItemsConstraintIsPreservedOnArrayProperty() {
    // given
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then
    @SuppressWarnings("unchecked")
    Schema<?> proceedingsSchema =
        (Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get("ApplyApplicationContentV2")
                .getProperties()
                .get("proceedings");
    assertThat(proceedingsSchema).isNotNull();
    assertThat(proceedingsSchema.getType()).isEqualTo("array");
    assertThat(proceedingsSchema.getMinItems()).isEqualTo(1);
  }

  @Test
  void
      givenSchemaWithTypeArray_whenCustomise_thenTypeArrayContainingNullIsConvertedToNullableField() {
    // given - Proceeding.json has fields with "type": ["string", "null"]
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then - a field with "type": ["string", "null"] becomes type=string + nullable=true
    @SuppressWarnings("unchecked")
    Schema<?> categoryOfLaw =
        (Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get("Proceeding")
                .getProperties()
                .get("categoryOfLaw");
    assertThat(categoryOfLaw.getType()).isEqualTo("string");
    assertThat(categoryOfLaw.getNullable()).isTrue();
  }

  @Test
  void givenSchemaWithRelativeRef_whenCustomise_thenRelativeRefIsTranslatedToOpenApiComponentRef() {
    // given - Applicant.json has addresses.items.$ref: "Address.json"
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then
    @SuppressWarnings("unchecked")
    Schema<?> addressesSchema =
        (Schema<?>)
            openApi.getComponents().getSchemas().get("Applicant").getProperties().get("addresses");
    Schema<?> addressesItems = addressesSchema.getItems();
    assertThat(addressesItems.get$ref()).isEqualTo("#/components/schemas/Address");
  }

  @Test
  void
      givenSchemaWithAdditionalProperties_whenCustomise_thenAdditionalPropertiesBooleanIsPreserved() {
    // given
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then - a schema with additionalProperties: true has it carried through convertNode
    Schema<?> schema = openApi.getComponents().getSchemas().get("ApplyApplicationContentV1");
    assertThat(schema.getAdditionalProperties()).isEqualTo(Boolean.TRUE);
  }

  @Test
  void givenComponentsWithNullSchemasMap_whenCustomise_thenSchemasAreInitialisedAndRegistered() {
    // given
    OpenAPI openApi = new OpenAPI();
    openApi.setComponents(new Components());

    // when/then
    assertThatCode(() -> customizer.customise(openApi)).doesNotThrowAnyException();
  }

  @Test
  void givenOpenApiWithNoApplicationCreateRequest_whenCustomise_thenNoExceptionThrown() {
    // given
    OpenAPI openApi = new OpenAPI();
    Components components = new Components();
    components.setSchemas(new LinkedHashMap<>());
    openApi.setComponents(components);

    // when/then
    assertThatCode(() -> customizer.customise(openApi)).doesNotThrowAnyException();
  }

  @Test
  void givenOpenApiWithApplicationCreateRequest_whenCustomise_thenApplicationContentNotRewired() {
    // given - ApplicationCreateRequest schema with a plain applicationContent property
    OpenAPI openApi = openApiWithApplicationCreateRequest();

    // when
    customizer.customise(openApi);

    // then - applicationContent is left as-is; no $ref is injected by the customiser
    @SuppressWarnings("unchecked")
    Schema<?> applicationContent =
        (Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get("ApplicationCreateRequest")
                .getProperties()
                .get("applicationContent");
    assertThat(applicationContent.get$ref()).isNull();
  }

  private OpenAPI openApiWithComponents() {
    OpenAPI openApi = new OpenAPI();
    openApi.setComponents(new Components());
    return openApi;
  }

  private OpenAPI openApiWithApplicationCreateRequest() {
    OpenAPI openApi = openApiWithComponents();
    Schema<?> contentProperty = new Schema<>();
    Schema<?> requestSchema = new Schema<>();
    requestSchema.setProperties(new LinkedHashMap<>(Map.of("applicationContent", contentProperty)));
    openApi
        .getComponents()
        .setSchemas(new LinkedHashMap<>(Map.of("ApplicationCreateRequest", requestSchema)));
    return openApi;
  }
}
