package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreateApplicationExamplesCustomizerTest {

  private static final String MEDIA_TYPE_JSON = "application/json";

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final CreateApplicationExamplesCustomizer customizer =
      new CreateApplicationExamplesCustomizer(objectMapper);

  @Test
  void givenNonMatchingOperationId_whenCustomize_thenSkipCustomisation() {
    // given
    Operation operation = new Operation();
    operation.setOperationId("differentOperation");

    // when
    Operation result = customizer.customize(operation, null);

    // then
    assertThat(result).isSameAs(operation);
    assertThat(operation.getRequestBody()).isNull();
  }

  @Test
  void givenCreateApplicationOperation_whenCustomize_thenCreateAllSchemaExamples() {
    // given
    Operation operation = createOperationWithBody();
    operation.setOperationId("createApplication");

    // when
    customizer.customize(operation, null);

    // then
    MediaType mediaType = operation.getRequestBody().getContent().get(MEDIA_TYPE_JSON);
    assertThat(mediaType).isNotNull();
    assertThat(mediaType.getExamples()).containsKeys("apply_v1", "apply_v2", "css_v1");
  }

  @Test
  void givenCreateApplicationOperation_whenCustomize_thenGenerateExpectedApplyV2Shape() {
    // given
    Operation operation = createOperationWithBody();
    operation.setOperationId("createApplication");

    // when
    customizer.customize(operation, null);

    // then
    Example example =
        operation.getRequestBody().getContent().get(MEDIA_TYPE_JSON).getExamples().get("apply_v2");
    assertThat(example).isNotNull();

    @SuppressWarnings("unchecked")
    Map<String, Object> value = (Map<String, Object>) example.getValue();
    assertThat(value).containsKeys("status", "applicationContent", "laaReference", "individuals");

    @SuppressWarnings("unchecked")
    Map<String, Object> content = (Map<String, Object>) value.get("applicationContent");
    assertThat(content)
        .containsKeys("id", "submittedAt", "office", "proceedings", "applicant", "laaReference");
    assertThat(content.get("submittedAt")).isEqualTo("2024-03-21T09:00:00Z");

    @SuppressWarnings("unchecked")
    Map<String, Object> applicant = (Map<String, Object>) content.get("applicant");
    assertThat(applicant).containsKeys("id", "addresses");
    assertThat(applicant.get("id")).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
  }

  @Test
  void givenCreateApplicationOperation_whenCustomize_thenGenerateExpectedCssV1Shape() {
    // given
    Operation operation = createOperationWithBody();
    operation.setOperationId("createApplication");

    // when
    customizer.customize(operation, null);

    // then
    Example example =
        operation.getRequestBody().getContent().get(MEDIA_TYPE_JSON).getExamples().get("css_v1");
    assertThat(example).isNotNull();

    @SuppressWarnings("unchecked")
    Map<String, Object> value = (Map<String, Object>) example.getValue();

    @SuppressWarnings("unchecked")
    Map<String, Object> content = (Map<String, Object>) value.get("applicationContent");
    assertThat(content).containsKeys("id", "submittedAt", "laaReference", "proceedings", "office");
    assertThat(content).doesNotContainKey("applicant");
  }

  @Test
  void givenMissingRequestBody_whenCustomize_thenCreateRequestBodyAndContent() {
    // given
    Operation operation = new Operation();
    operation.setOperationId("createApplication");

    // when
    customizer.customize(operation, null);

    // then
    assertThat(operation.getRequestBody()).isNotNull();
    assertThat(operation.getRequestBody().getContent()).isNotNull();
    assertThat(operation.getRequestBody().getContent().get(MEDIA_TYPE_JSON)).isNotNull();
  }

  private Operation createOperationWithBody() {
    Operation operation = new Operation();
    RequestBody requestBody = new RequestBody();
    Content content = new Content();
    MediaType mediaType = new MediaType();
    content.addMediaType(MEDIA_TYPE_JSON, mediaType);
    requestBody.setContent(content);
    operation.setRequestBody(requestBody);
    return operation;
  }
}
