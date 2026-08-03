package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.ReadyApplicationRequest;

class ReadyApplicationCommandMapperTest {

  private final ReadyApplicationCommandMapper mapper =
      new ReadyApplicationCommandMapper(JsonMapper.builder().build());

  @Test
  void givenReadyRequest_whenMapped_thenPreservesVersionAndAuditPayload() {
    UUID applicationId = UUID.randomUUID();
    ReadyApplicationRequest request = new ReadyApplicationRequest().applicationVersion(4L);
    Instant before = Instant.now();

    var command = mapper.toCommand(applicationId, request);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.expectedApplicationVersion()).isEqualTo(4L);
    assertThat(command.serialisedRequest()).isEqualTo("{\"applicationVersion\":4}");
    assertThat(command.occurredAt()).isBetween(before, Instant.now());
  }
}
