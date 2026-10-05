package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.config.swagger.ApplicationLinkRequestExampleCustomizer;

class ApplicationLinkRequestExampleCustomizerTest {

  private final ApplicationLinkRequestExampleCustomizer customizer =
      new ApplicationLinkRequestExampleCustomizer();

  @Test
  void givenLinkApplicationRequestBody_whenCustomise_thenBothNamedExamplesAreAdded() {
    // given
    OpenAPI openApi = openApiWithLinkApplicationOperation();

    // when
    customizer.customise(openApi);

    // then
    MediaType mediaType =
        openApi
            .getPaths()
            .get("/api/v0/applications/{id}/link")
            .getPost()
            .getRequestBody()
            .getContent()
            .get("application/json");
    assertThat(mediaType.getExamples()).containsKeys("firstTimeLink", "targetAlreadyLinked");
    assertThat(mediaType.getExamples().get("firstTimeLink").getValue())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .doesNotContainKey("linkedGroupVersion");
    assertThat(mediaType.getExamples().get("targetAlreadyLinked").getValue())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("linkedGroupVersion", 2);
  }

  @Test
  void givenNoMatchingPath_whenCustomise_thenNoExceptionIsThrown() {
    // given
    OpenAPI openApi = new OpenAPI().paths(new Paths());

    // when / then
    org.assertj.core.api.Assertions.assertThatCode(() -> customizer.customise(openApi))
        .doesNotThrowAnyException();
  }

  private OpenAPI openApiWithLinkApplicationOperation() {
    RequestBody requestBody =
        new RequestBody().content(new Content().addMediaType("application/json", new MediaType()));
    Operation operation = new Operation().requestBody(requestBody);
    PathItem pathItem = new PathItem().post(operation);
    Paths paths = new Paths();
    paths.addPathItem("/api/v0/applications/{id}/link", pathItem);
    return new OpenAPI().paths(paths);
  }
}
