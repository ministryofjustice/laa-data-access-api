package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class UpdatePriorAuthorityDraftCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Test
  void givenExistingDraft_whenHandle_thenEmitsEventCarryingContentWithStartedType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent existingContent =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Existing", null, null, null);
    PriorAuthorityContent updatedContent =
        new PriorAuthorityContent(PriorAuthorityType.COUNSEL, "Updated", null, null, null);
    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId, updatedContent, "new", 2, "PriorAuthority.json", OCCURRED_AT);
    when(priorAuthority.getState())
        .thenReturn(draftState(priorAuthorityId, applicationId, Erasable.of(existingContent)));

    new UpdatePriorAuthorityDraftCommandHandler().handle(command, priorAuthority, eventAppender);

    verify(eventAppender)
        .append(
            new PriorAuthorityDraftUpdatedEvent(
                priorAuthorityId,
                applicationId,
                OCCURRED_AT,
                Erasable.of(updatedContent.withPriorAuthorityType(PriorAuthorityType.EXPERT)),
                Erasable.of("new")));
  }

  @Test
  void givenMissingDraft_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Updated", null, null, null),
            "new",
            2,
            "PriorAuthority.json",
            OCCURRED_AT);
    when(priorAuthority.getState()).thenReturn(new PriorAuthorityState());

    assertThatThrownBy(
            () ->
                new UpdatePriorAuthorityDraftCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority " + priorAuthorityId + " not found");

    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenErasedDraftContent_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Updated", null, null, null),
            "new",
            2,
            "PriorAuthority.json",
            OCCURRED_AT);
    when(priorAuthority.getState())
        .thenReturn(draftState(priorAuthorityId, UUID.randomUUID(), Erasable.erased()));

    assertThatThrownBy(
            () ->
                new UpdatePriorAuthorityDraftCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class);

    verifyNoInteractions(eventAppender);
  }

  private static PriorAuthorityState draftState(
      UUID priorAuthorityId, UUID applicationId, Erasable<PriorAuthorityContent> content) {
    PriorAuthorityState state = new PriorAuthorityState();
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId,
            applicationId,
            "EXPERT",
            1,
            OCCURRED_AT,
            "1A001B",
            content,
            Erasable.of("{}")));
    return state;
  }
}
