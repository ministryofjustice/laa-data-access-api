package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;

/** Unit tests for {@link PriorAuthorityDecider}. */
class PriorAuthorityDeciderTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T10:00:00Z");

  @Test
  void givenCommand_whenDecideStartDraft_thenReturnsEventWithExpectedFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            OCCURRED_AT);
    PriorAuthorityDraftStartedEvent event = PriorAuthorityDecider.decideStartDraft(command);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT.name());
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenSubmitCommand_whenDecideSubmit_thenAlwaysUsesDataVersionZero() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityState state = new PriorAuthorityState();
    state.applicationId = UUID.randomUUID();
    state.priorAuthorityType = PriorAuthorityType.COUNSEL.name();
    state.schemaVersion = 2;
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, OCCURRED_AT);

    PriorAuthoritySubmittedEvent event = PriorAuthorityDecider.decideSubmit(command, state);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(state.applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo(PriorAuthorityType.COUNSEL.name());
    assertThat(event.schemaVersion()).isEqualTo(2);
    assertThat(event.dataVersion()).isEqualTo(0L);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }
}
