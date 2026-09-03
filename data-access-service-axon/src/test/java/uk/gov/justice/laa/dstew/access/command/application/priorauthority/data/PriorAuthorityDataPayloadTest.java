package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriorAuthorityDataPayloadTest {

  @Test
  void fiveArgumentConstructorDefaultsAssignmentDescriptionToNull() {
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "{}",
            Instant.parse("2026-08-28T10:00:00Z"));

    assertThat(payload.assignmentDescription()).isNull();
  }
}
