package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDraftV2Request;

class ApplicationDraftV2CommandMapperTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final ApplicationDraftV2CommandMapper mapper =
      new ApplicationDraftV2CommandMapper(objectMapper);

  @Test
  void createCommandCarriesMetadataButSerialisedRequestExcludesIt() {
    UUID applicationId = UUID.randomUUID();
    var request =
        new CreateApplicationDraftV2Request()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .laaReference("2-123456")
            .applicationContent(Map.of("incomplete", true))
            .metadata(Map.of("currentStep", "client"));

    CreateApplicationDraftV2Command command = mapper.toCreateCommand(request, 2);

    assertThat(command.metadata()).containsEntry("currentStep", "client");
    assertThat(command.schemaVersion()).isEqualTo(2);
    JsonNode business = readJson(command.serialisedRequest());
    assertThat(business.has("metadata")).isFalse();
    assertThat(business.get("laaReference").asText()).isEqualTo("2-123456");
  }

  @Test
  void updateCommandCarriesMetadataButSerialisedRequestExcludesIt() {
    UUID applicationId = UUID.randomUUID();
    var request =
        new UpdateApplicationDraftV2Request()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .laaReference("2-123456")
            .applicationContent(Map.of("incomplete", true))
            .metadata(Map.of("currentStep", "proceedings"));

    UpdateApplicationDraftCommand command = mapper.toUpdateCommand(applicationId, request, null);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.metadata()).containsEntry("currentStep", "proceedings");
    assertThat(readJson(command.serialisedRequest()).has("metadata")).isFalse();
  }

  private JsonNode readJson(String json) {
    return objectMapper.readTree(json);
  }
}
