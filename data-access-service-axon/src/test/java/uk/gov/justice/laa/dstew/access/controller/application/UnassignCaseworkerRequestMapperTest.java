package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;

class UnassignCaseworkerRequestMapperTest {

  private final UnassignCaseworkerRequestMapper mapper =
      new UnassignCaseworkerRequestMapper(JsonMapper.builder().build());

  @Test
  void givenEventHistory_whenMapped_thenCreatesCommandWithAuditData() {
    UUID applicationId = UUID.randomUUID();
    CaseworkerUnassignRequest request =
        CaseworkerUnassignRequest.builder()
            .eventHistory(EventHistoryRequest.builder().eventDescription("Returned").build())
            .build();

    Instant before = Instant.now();

    var command = mapper.toCommand(applicationId, request);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.serialisedRequest()).contains("eventHistory", "Returned");
    assertThat(command.eventDescription()).isEqualTo("Returned");
    assertThat(command.occurredAt()).isBetween(before, Instant.now());
  }

  @Test
  void givenNoEventHistory_whenMapped_thenPreservesAbsentHistory() throws Exception {
    var command = mapper.toCommand(UUID.randomUUID(), CaseworkerUnassignRequest.builder().build());
    var payload = JsonMapper.builder().build().readTree(command.serialisedRequest());

    assertThat(payload.get("eventHistory").isNull()).isTrue();
    assertThat(command.eventDescription()).isNull();
  }
}
