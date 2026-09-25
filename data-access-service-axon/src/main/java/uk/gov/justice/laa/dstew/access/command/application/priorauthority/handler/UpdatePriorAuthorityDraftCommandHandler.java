package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@Component
public class UpdatePriorAuthorityDraftCommandHandler {

  @CommandHandler
  void handle(
      UpdatePriorAuthorityDraftCommand command,
      PriorAuthorityDraftStore draftStore,
      @InjectEntity(idProperty = "priorAuthorityId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    PriorAuthorityDataPayload existingDraft = requireDraft(command.priorAuthorityId(), draftStore);
    PriorAuthorityDataPayload payload =
        buildUpdatedDraftPayload(command, existingDraft, priorAuthority.getPriorAuthorityType());
    draftStore.upsert(
        command.priorAuthorityId(),
        priorAuthority.getApplicationId(),
        payload,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        PriorAuthorityDecider.decideDraftUpdated(command, priorAuthority.getApplicationId()));
  }

  private static @NonNull PriorAuthorityDataPayload requireDraft(
      UUID priorAuthorityId, PriorAuthorityDraftStore draftStore) {
    return draftStore
        .find(priorAuthorityId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Prior Authority %s not found".formatted(priorAuthorityId)));
  }

  private PriorAuthorityDataPayload buildUpdatedDraftPayload(
      UpdatePriorAuthorityDraftCommand command,
      PriorAuthorityDataPayload existingDraft,
      String priorAuthorityType) {
    PriorAuthorityContent updatedContent =
        command
            .content()
            .withPriorAuthorityType(PriorAuthorityType.valueOf(priorAuthorityType))
            .withUploadedDocuments(existingDraft.content().uploadedDocuments());
    return existingDraft
        .withContent(updatedContent)
        .withSerialisedRequest(command.serialisedRequest())
        .withSubmittedAt(command.occurredAt());
  }
}
