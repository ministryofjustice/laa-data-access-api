package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;

class CreateApplicationCommandMapperTest {

  private final CreateApplicationCommandMapper mapper =
      new CreateApplicationCommandMapper(JsonMapper.builder().build());

  @Test
  void givenCcsApplication_whenMapped_thenSelectsCssSchema() {
    var command = mapper.toCommand(request(ApplicationType.CCS), 1);

    assertThat(command.schemaName()).isEqualTo("CssApplication.json");
  }

  @Test
  void givenApplyApplication_whenMapped_thenSelectsApplySchema() {
    var command = mapper.toCommand(request(ApplicationType.APPLY), 2);

    assertThat(command.schemaName()).isEqualTo("ApplyApplication.json");
    assertThat(command.schemaVersion()).isEqualTo(2);
  }

  @Test
  void givenRequest_whenMapped_thenIndividualIdsAreNull() {
    var command = mapper.toCommand(request(ApplicationType.APPLY), 1);

    assertThat(command.individuals()).isEmpty();
  }

  @Test
  void givenRequest_whenMapped_thenApplyApplicationIdDerivedFromContent() {
    UUID applyApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request = request(ApplicationType.APPLY);
    request.setApplicationContent(Map.of("id", applyApplicationId.toString()));

    var command = mapper.toCommand(request, 1);

    assertThat(command.applyApplicationId()).isEqualTo(applyApplicationId);
  }

  private ApplicationCreateRequest request(ApplicationType applicationType) {
    return ApplicationCreateRequest.builder()
        .applicationType(applicationType)
        .applicationContent(Map.of())
        .build();
  }
}
