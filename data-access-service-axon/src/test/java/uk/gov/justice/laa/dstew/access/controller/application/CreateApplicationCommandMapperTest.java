package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class CreateApplicationCommandMapperTest {

  private final CreateApplicationCommandMapper mapper =
      new CreateApplicationCommandMapper(JsonMapper.builder().build());

  @Test
  void givenCcsApplication_whenMapped_thenSelectsCssSchema() {
    UUID id = UUID.randomUUID();
    var command = mapper.toCommand(request(ApplicationType.CCS, id), 1);

    assertThat(command.schemaName()).isEqualTo("CssApplication.json");
  }

  @Test
  void givenAnyOtherApplicationType_whenMapped_thenSelectsApplySchema() {
    UUID id = UUID.randomUUID();
    var command = mapper.toCommand(request(ApplicationType.APPLY, id), 2);

    assertThat(command.schemaName()).isEqualTo("ApplyApplication.json");
  }

  @Test
  void givenValidContentId_whenMapped_thenApplicationIdEqualsContentId() {
    UUID id = UUID.randomUUID();
    var command = mapper.toCommand(request(ApplicationType.APPLY, id), 1);

    assertThat(command.applicationId()).isEqualTo(id);
    assertThat(command.applyApplicationId()).isEqualTo(id);
  }

  @Test
  void givenMissingContentId_whenMapped_thenThrowsValidationException() {
    ApplicationCreateRequest request =
        ApplicationCreateRequest.builder()
            .applicationType(ApplicationType.APPLY)
            .applicationContent(Map.of())
            .build();

    assertThatThrownBy(() -> mapper.toCommand(request, 1)).isInstanceOf(ValidationException.class);
  }

  @Test
  void givenMalformedContentId_whenMapped_thenThrowsValidationException() {
    ApplicationCreateRequest request =
        ApplicationCreateRequest.builder()
            .applicationType(ApplicationType.APPLY)
            .applicationContent(Map.of("id", "not-a-uuid"))
            .build();

    assertThatThrownBy(() -> mapper.toCommand(request, 1)).isInstanceOf(ValidationException.class);
  }

  @Test
  void givenNullContentId_whenMapped_thenThrowsValidationException() {
    Map<String, Object> content = new HashMap<>();
    content.put("id", null);
    ApplicationCreateRequest request =
        ApplicationCreateRequest.builder()
            .applicationType(ApplicationType.APPLY)
            .applicationContent(content)
            .build();

    assertThatThrownBy(() -> mapper.toCommand(request, 1)).isInstanceOf(ValidationException.class);
  }

  private ApplicationCreateRequest request(ApplicationType applicationType, UUID contentId) {
    return ApplicationCreateRequest.builder()
        .applicationType(applicationType)
        .applicationContent(Map.of("id", contentId.toString()))
        .build();
  }
}
