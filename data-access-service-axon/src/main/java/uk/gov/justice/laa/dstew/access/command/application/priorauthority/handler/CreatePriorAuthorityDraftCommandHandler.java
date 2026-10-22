package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.List;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.security.OfficeCodeWriteAccessPolicy;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Handles creation of prior-authority drafts. */
@Component
public class CreatePriorAuthorityDraftCommandHandler {

  /** Creates a new prior-authority draft and emits the corresponding event. */
  @CommandHandler
  public void handle(
      CreatePriorAuthorityDraftCommand command,
      PriorAuthorityDraftStore draftStore,
      PriorAuthorityDataStore dataStore,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      OfficeCodeWriteAccessPolicy writeAccessPolicy,
      EventAppender eventAppender) {

    if (draftStore.exists(command.priorAuthorityId())
        || dataStore.exists(command.priorAuthorityId())) {
      throw new PriorAuthorityCreationConflictException(command.priorAuthorityId());
    }

    if (!application.isGranted()) {
      throw new ValidationException(
          List.of(
              "Prior authority requires the application to have an overall decision of GRANTED"));
    }

    String officeCode = application.getOfficeCode();
    writeAccessPolicy.requireWriteAccess(officeCode, command.caller());
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            command.priorAuthorityId(),
            command.applicationId(),
            command.content(),
            command.serialisedRequest(),
            command.occurredAt(),
            null,
            officeCode,
            null);

    draftStore.upsert(
        command.priorAuthorityId(),
        command.applicationId(),
        payload,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(PriorAuthorityDecider.decideStartDraft(command, officeCode));
  }
}
