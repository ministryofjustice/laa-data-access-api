package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.justice.laa.dstew.access.config.swagger.ApplicationContentSchemaCustomizer;

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
            "Proceeding",
            "Provider",
            "Client",
            "Opponent",
            "ScopeLimitation",
            "Child",
            "ApplyApplicationContentV1");
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
    assertThat(schema.getRequired())
        .containsExactlyInAnyOrder("createdAt", "submittedAt", "proceedings", "provider", "client");
  }

  @Test
  void
      givenSchemaWithMinItemsConstraint_whenCustomise_thenMinItemsConstraintIsPreservedOnArrayProperty() {
    // given
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then
    Schema<?> proceedingsSchema =
        (Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get("ApplyApplicationContentV1")
                .getProperties()
                .get("proceedings");
    assertThat(proceedingsSchema).isNotNull();
    assertThat(proceedingsSchema.getTypes()).containsExactly("array");
    assertThat(proceedingsSchema.getMinItems()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "Proceeding, delegatedFunctionsDate, string",
    "Client, hasNationalInsuranceNumber, boolean"
  })
  void givenSchemaWithNullableTypeArray_whenCustomise_thenOpenApiTypeUnionIsPreserved(
      String componentName, String propertyName, String propertyType) {
    // given
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then
    @SuppressWarnings("unchecked")
    Schema<?> nullableProperty =
        (Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get(componentName)
                .getProperties()
                .get(propertyName);
    assertThat(nullableProperty.getTypes()).containsExactlyInAnyOrder(propertyType, "null");
    assertThat(nullableProperty.getNullable()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "Client, appliedPreviously, boolean",
    "Client, firstName, string",
    "Client, addresses, array",
    "Proceeding, substantiveLevelOfService, integer"
  })
  void givenSchemaWithScalarType_whenCustomise_thenOpenApiTypeSetIsPopulated(
      String componentName, String propertyName, String propertyType) {
    // given
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then
    Schema<?> property =
        (Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get(componentName)
                .getProperties()
                .get(propertyName);
    assertThat(property.getTypes()).containsExactly(propertyType);
  }

  @Test
  void givenSchemaWithRelativeRef_whenCustomise_thenRelativeRefIsTranslatedToOpenApiComponentRef() {
    // given - Client.json has addresses.items.$ref: "Address.json"
    OpenAPI openApi = openApiWithComponents();

    // when
    customizer.customise(openApi);

    // then
    @SuppressWarnings("unchecked")
    Schema<?> addressesSchema =
        (Schema<?>)
            openApi.getComponents().getSchemas().get("Client").getProperties().get("addresses");
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

    // then - a schema with additionalProperties: false has it carried through convertNode
    Schema<?> schema = openApi.getComponents().getSchemas().get("ApplyApplicationContentV1");
    assertThat(schema.getAdditionalProperties()).isEqualTo(Boolean.FALSE);
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
  void givenApplicationCreateRequestWithNullProperties_whenCustomise_thenReturnsEarlyGracefully() {
    // given - ApplicationCreateRequest exists in the schemas map but has no properties set
    OpenAPI openApi = openApiWithComponents();
    Schema<?> requestSchemaWithNullProperties = new Schema<>();
    // properties left null (not explicitly set)
    openApi
        .getComponents()
        .setSchemas(
            new LinkedHashMap<>(
                Map.of("ApplicationCreateRequest", requestSchemaWithNullProperties)));

    // when/then - no exception thrown; customiser exits the null-properties guard without wiring
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
  void
      givenOpenApiWithApplicationCreateRequest_whenCustomise_thenApplicationContentRefsVersionedSchema() {
    // given - ApplicationCreateRequest schema with a plain applicationContent property
    OpenAPI openApi = openApiWithApplicationCreateRequest();

    // when
    customizer.customise(openApi);

    // then - applicationContent uses a $ref to the application content schema
    @SuppressWarnings("unchecked")
    Schema<?> applicationContent =
        (Schema<?>)
            openApi
                .getComponents()
                .getSchemas()
                .get("ApplicationCreateRequest")
                .getProperties()
                .get("applicationContent");
    assertThat(applicationContent.get$ref())
        .isEqualTo("#/components/schemas/ApplyApplicationContentV1");
    assertThat(applicationContent.getDescription())
        .isEqualTo("Application content conforming to the versioned schema");
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
