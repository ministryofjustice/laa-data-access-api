package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

@DisplayName("CreateApplicationExamplesCustomizer")
class CreateApplicationExamplesCustomizerTest {

  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());
  private final CreateApplicationExamplesCustomizer customizer =
      new CreateApplicationExamplesCustomizer(objectMapper);

  @Test
  @DisplayName("should skip customization for non-matching operation")
  void testSkipsNonMatchingOperation() {
    Operation operation = new Operation();
    operation.setOperationId("differentOperation");

    Operation result = customizer.customize(operation, mock(HandlerMethod.class));

    assertThat(result).isSameAs(operation);
    assertThat(operation.getRequestBody()).isNull();
  }

  @Test
  @DisplayName("should customize createApplication operation with all variants")
  void testCustomizesCreateApplicationOperation() {
    Operation operation = createOperationWithBody();
    operation.setOperationId("createApplication");

    customizer.customize(operation, mock(HandlerMethod.class));

    assertThat(operation.getRequestBody()).isNotNull();
    MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
    assertThat(mediaType.getExamples()).containsKeys(
        "v1_apply_example",
        "v2_apply_example",
        "decide_example"
    );
  }

  @Test
  @DisplayName("should generate v1_apply example with correct content")
  void testV1ApplyExample() {
    Operation operation = createOperationWithBody();
    operation.setOperationId("createApplication");

    customizer.customize(operation, mock(HandlerMethod.class));

    Example v1Example = operation.getRequestBody().getContent()
        .get("application/json").getExamples()
        .get("v1_apply_example");

    assertThat(v1Example).isNotNull();
    assertThat(v1Example.getSummary()).contains("V1 Apply", "ApplyApplication", "objectType=apply");

    @SuppressWarnings("unchecked")
    Map<String, Object> value = (Map<String, Object>) v1Example.getValue();
    assertThat(value).containsKeys("status", "laaReference", "applicationContent", "individuals");

    @SuppressWarnings("unchecked")
    Map<String, Object> content = (Map<String, Object>) value.get("applicationContent");
    assertThat(content.get("objectType")).isEqualTo("apply");
  }

  @Test
  @DisplayName("should generate v2_apply example with proceedings")
  void testV2ApplyExample() {
    Operation operation = createOperationWithBody();
    operation.setOperationId("createApplication");

    customizer.customize(operation, mock(HandlerMethod.class));

    Example v2Example = operation.getRequestBody().getContent()
        .get("application/json").getExamples()
        .get("v2_apply_example");

    assertThat(v2Example).isNotNull();
    assertThat(v2Example.getSummary()).contains("V2 Apply", "ApplyApplication1", "objectType=applyV2");

    @SuppressWarnings("unchecked")
    Map<String, Object> value = (Map<String, Object>) v2Example.getValue();
    @SuppressWarnings("unchecked")
    Map<String, Object> content = (Map<String, Object>) value.get("applicationContent");

    assertThat(content.get("objectType")).isEqualTo("applyV2");
    assertThat(content).containsKey("proceedings");
  }

  @Test
  @DisplayName("should generate decide example with office field")
  void testDecideExample() {
    Operation operation = createOperationWithBody();
    operation.setOperationId("createApplication");

    customizer.customize(operation, mock(HandlerMethod.class));

    Example decideExample = operation.getRequestBody().getContent()
        .get("application/json").getExamples()
        .get("decide_example");

    assertThat(decideExample).isNotNull();
    assertThat(decideExample.getSummary()).contains("Decide", "DecideApplication", "objectType=decide");

    @SuppressWarnings("unchecked")
    Map<String, Object> value = (Map<String, Object>) decideExample.getValue();
    assertThat(value.get("status")).isEqualTo("APPLICATION_SUBMITTED");

    @SuppressWarnings("unchecked")
    Map<String, Object> content = (Map<String, Object>) value.get("applicationContent");
    assertThat(content.get("objectType")).isEqualTo("decide");
    assertThat(content.get("office")).isEqualTo("LON001");
  }

  @Test
  @DisplayName("should include individual details in all examples")
  void testIndividualDetailsInAllExamples() {
    Operation operation = createOperationWithBody();
    operation.setOperationId("createApplication");

    customizer.customize(operation, mock(HandlerMethod.class));

    Map<String, Example> examples = operation.getRequestBody().getContent()
        .get("application/json").getExamples();

    for (String key : examples.keySet()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> value = (Map<String, Object>) examples.get(key).getValue();
      assertThat(value).containsKey("individuals");
      assertThat((java.util.List<?>) value.get("individuals")).isNotEmpty();
    }
  }

  @Test
  @DisplayName("should create request body if not present")
  void testCreatesRequestBodyWhenAbsent() {
    Operation operation = new Operation();
    operation.setOperationId("createApplication");

    customizer.customize(operation, mock(HandlerMethod.class));

    assertThat(operation.getRequestBody()).isNotNull();
    assertThat(operation.getRequestBody().getContent()).isNotNull();
  }

  private Operation createOperationWithBody() {
    Operation operation = new Operation();
    RequestBody requestBody = new RequestBody();
    Content content = new Content();
    MediaType mediaType = new MediaType();
    content.addMediaType("application/json", mediaType);
    requestBody.setContent(content);
    operation.setRequestBody(requestBody);
    return operation;
  }
}
