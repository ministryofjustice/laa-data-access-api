package uk.gov.justice.laa.dstew.access.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/**
 * Generates named Swagger UI examples for the {@code createApplication} operation directly from the
 * JSON Schema definition files. The {@code applicationContent} in each example is built by
 * introspecting the schema's properties, so renaming or adding a field in the schema file
 * automatically updates the Swagger example on next restart — no Java changes needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateApplicationExamplesCustomizer implements OperationCustomizer {

  private static final String CREATE_APPLICATION_OPERATION_ID = "createApplication";
  private static final String MEDIA_TYPE =
      org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

  private final ObjectMapper objectMapper;
  private final SchemaExampleGenerator schemaExampleGenerator;

  /**
   * Each variant points to an actual JSON Schema definition file. The example shown in Swagger UI
   * is generated from that file's {@code properties}, so it always matches the live schema.
   */
  List<ExampleVariant> variants =
      List.of(
          new ExampleVariant(
              "apply_v1",
              "APPLY — version 1 (id + submittedAt required)",
              "schema/1/ApplyApplication.json"));

  // TODO: activate additional variants once the applicationContent schema structure has been
  // confirmed.
  //  new ExampleVariant(
  //      "apply_v2",
  //      "APPLY — version 2 (id, submittedAt, office, proceedings, applicant required)",
  //      "schema/2/ApplyApplication.json"),
  //  new ExampleVariant(
  //      "css_v1",
  //      "CSS — version 1 (id, submittedAt, laaReference required)",
  //      "schema/1/CssApplication.json")

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    if (!CREATE_APPLICATION_OPERATION_ID.equals(operation.getOperationId())) {
      return operation;
    }

    Map<String, Example> examples = new LinkedHashMap<>();
    for (ExampleVariant variant : variants) {
      Map<String, Object> applicationContent =
          schemaExampleGenerator.generateExampleFromSchema(variant.schemaPath());
      if (applicationContent != null) {
        ApplicationCreateRequest request = buildRequestWrapper(applicationContent);
        Example example = new Example();
        example.setSummary(variant.summary());
        // Convert via the Spring ObjectMapper (JavaTimeModule configured) so the example value
        // is plain Map/List/primitives rather than a typed Java object. This ensures
        // swagger-core can render it correctly regardless of its own Jackson configuration.
        example.setValue(objectMapper.convertValue(request, Object.class));
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
    MediaType mediaType =
        content.computeIfAbsent(
            MEDIA_TYPE,
            k -> {
              MediaType mt = new MediaType();
              mt.setSchema(new Schema<>().$ref("#/components/schemas/ApplicationCreateRequest"));
              return mt;
            });
    mediaType.setExamples(examples);
    return operation;
  }

  /**
   * Wraps the schema-generated {@code applicationContent} in a typed {@link
   * ApplicationCreateRequest} using the generated model builders.
   *
   * <p>This method is schema-agnostic: {@code applicationContent} is always {@code Map<String,
   * Object>}, so the same wrapper applies uniformly to all schema variants (APPLY v1, APPLY v2, CSS
   * v1) without any per-file branching.
   */
  private ApplicationCreateRequest buildRequestWrapper(Map<String, Object> applicationContent) {
    IndividualCreateRequest individual =
        IndividualCreateRequest.builder()
            .firstName("Jane")
            .lastName("Smith")
            .dateOfBirth(LocalDate.of(1990, 1, 15))
            .type(IndividualType.CLIENT)
            .details(Map.of("niNumber", "AB123456C"))
            .build();

    return ApplicationCreateRequest.builder()
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("LAA-000-001")
        .applicationContent(applicationContent)
        .individuals(List.of(individual))
        .build();
  }

  record ExampleVariant(String key, String summary, String schemaPath) {}
}
