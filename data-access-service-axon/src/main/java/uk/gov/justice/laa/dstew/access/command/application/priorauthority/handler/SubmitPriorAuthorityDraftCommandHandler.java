package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/** Handles submission of prior-authority drafts. */
@Component
public class SubmitPriorAuthorityDraftCommandHandler {

  /** Submits a prior-authority draft, validates its schema, and emits the submitted event. */
  @CommandHandler
  public void handle(
      SubmitPriorAuthorityDraftCommand command,
      PriorAuthorityDraftStore draftStore,
      PriorAuthorityDataStore dataStore,
      ApplicationDataStore applicationDataStore,
      JsonSchemaValidator jsonSchemaValidator,
      @InjectEntity(idProperty = "priorAuthorityId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    PriorAuthorityDataPayload payload =
        draftStore
            .find(command.priorAuthorityId())
            .orElseThrow(
                () ->
                    new uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException(
                        "Prior Authority draft not found: " + command.priorAuthorityId()));

    jsonSchemaValidator.validate(payload.content(), "PriorAuthority.json", 1);

    dataStore.append(
        command.priorAuthorityId(),
        0L,
        priorAuthority.getApplicationId(),
        payload,
        payload.serialisedRequest(),
        command.occurredAt());

    long applicationDataVersion =
        applicationDataStore.latestVersion(priorAuthority.getApplicationId());

    // Emit submitted event with the application version reference
    eventAppender.append(
        new PriorAuthoritySubmittedEvent(
            command.priorAuthorityId(),
            priorAuthority.getApplicationId(),
            payload.content().priorAuthorityType() != null
                ? payload.content().priorAuthorityType().name()
                : null,
            1,
            0L,
            applicationDataVersion,
            command.occurredAt()));

    draftStore.delete(command.priorAuthorityId());
  }
}
