package uk.gov.justice.laa.dstew.access.command.application.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApplicationDraftUpdatedEventTest {

  @Test
  void storedEventCarriesOnlyIdentifierAndTimestamp() throws Exception {
    var event =
        new ApplicationDraftUpdatedEvent(UUID.randomUUID(), Instant.parse("2026-01-02T10:00:00Z"));
    var mapper = JsonMapper.builder().build();

    JsonNode json = mapper.readTree(mapper.writeValueAsString(event));

    assertThat(json.propertyNames()).containsExactlyInAnyOrder("applicationId", "occurredAt");
  }
}
