package uk.gov.justice.laa.dstew.access.config.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Adds named request-body examples to the {@code linkApplication} operation. The {@code spring}
 * openapi-generator templates used for {@link
 * uk.gov.justice.laa.dstew.access.api.ApplicationCommandApi} do not carry {@code
 * requestBody.content.*.examples} from the source spec into the generated
 * {@code @Parameter}/{@code @RequestBody} annotations, so springdoc's runtime-derived document
 * would otherwise show Swagger UI's synthesised default ({@code linkedGroupVersion: 0}) for every
 * request, misleading callers linking a standalone application for the first time.
 */
@Component
@ExcludeFromGeneratedCodeCoverage
public class ApplicationLinkRequestExampleCustomizer implements OpenApiCustomizer {

  private static final String LINK_PATH = "/api/v0/applications/{id}/link";

  @Override
  public void customise(OpenAPI openApi) {
    if (openApi.getPaths() == null) {
      return;
    }
    var pathItem = openApi.getPaths().get(LINK_PATH);
    if (pathItem == null || pathItem.getPost() == null) {
      return;
    }
    Operation operation = pathItem.getPost();
    RequestBody requestBody = operation.getRequestBody();
    if (requestBody == null || requestBody.getContent() == null) {
      return;
    }
    MediaType mediaType = requestBody.getContent().get("application/json");
    if (mediaType == null) {
      return;
    }
    mediaType.setExamples(buildExamples());
  }

  private Map<String, Example> buildExamples() {
    var firstTimeLink = new Example();
    firstTimeLink.setSummary("Target is standalone (first-time link)");
    firstTimeLink.setDescription(
        "Omit linkedGroupVersion entirely; the target is not yet in a group.");
    firstTimeLink.setValue(
        Map.of(
            "applicationId", "3fa85f64-5717-4562-b3fc-2c963f66afa7",
            "linkType", "FAMILY"));

    var targetAlreadyLinked = new Example();
    targetAlreadyLinked.setSummary("Target already belongs to a group");
    targetAlreadyLinked.setDescription(
        "Supply the target group's linkedGroupVersion exactly as last read.");
    targetAlreadyLinked.setValue(
        Map.of(
            "applicationId", "3fa85f64-5717-4562-b3fc-2c963f66afa7",
            "linkType", "FAMILY",
            "linkedGroupVersion", 2));

    Map<String, Example> examples = new LinkedHashMap<>();
    examples.put("firstTimeLink", firstTimeLink);
    examples.put("targetAlreadyLinked", targetAlreadyLinked);
    return examples;
  }
}
