package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class UpdatePriorAuthorityDraftCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Captor private ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor;

  @Test
  void givenExistingDraft_whenHandle_thenUpdatesDraftAndEmitsEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityContent existingContent =
        new PriorAuthorityContent(PriorAuthorityType.COUNSEL, "Existing", null, null, null);
    PriorAuthorityDataPayload existingDraft =
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            existingContent,
            "old",
            OCCURRED_AT,
            null,
            "1A001B",
            Map.of(documentId, "file.pdf"));
    PriorAuthorityContent updatedContent =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Updated", null, null, null);
    final UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId, updatedContent, "new", 2, "PriorAuthority.json", OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getPriorAuthorityType()).thenReturn(PriorAuthorityType.EXPERT.name());
    when(priorAuthority.getApplicationId()).thenReturn(applicationId);

    new UpdatePriorAuthorityDraftCommandHandler()
        .handle(command, draftStore, priorAuthority, eventAppender);

    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("new"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().content().priorAuthorityType())
        .isEqualTo(PriorAuthorityType.EXPERT);
    assertThat(payloadCaptor.getValue().documentFilenames()).containsEntry(documentId, "file.pdf");
    assertThat(payloadCaptor.getValue().serialisedRequest()).isEqualTo("new");
    assertThat(payloadCaptor.getValue().submittedAt()).isEqualTo(OCCURRED_AT);
    verify(eventAppender)
        .append(new PriorAuthorityDraftUpdatedEvent(priorAuthorityId, applicationId, OCCURRED_AT));
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

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new UpdatePriorAuthorityDraftCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority " + priorAuthorityId + " not found");

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(priorAuthority, eventAppender);
  }
}
