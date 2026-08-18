package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;

class CreateApplicationCommandMapperTest {

  private final CreateApplicationCommandMapper mapper =
      new CreateApplicationCommandMapper(JsonMapper.builder().build());

  @Test
  void givenRequest_whenMapped_thenSelectsApplySchema() {
    UUID id = UUID.randomUUID();
    var command = mapper.toCommand(request(id), 2);

    assertThat(command.schemaName()).isEqualTo("BaseCivilApplication.json");
  }

  @Test
  void givenValidId_whenMapped_thenApplicationIdEqualsRequestId() {
    UUID id = UUID.randomUUID();
    var command = mapper.toCommand(request(id), 1);

    assertThat(command.applicationId()).isEqualTo(id);
  }

  private ApplicationCreateRequest request(UUID id) {
    return ApplicationCreateRequest.builder().id(id).applicationContent(Map.of()).build();
  }
}
