package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;

/** Handles submission of application drafts. */
@Component
public class SubmitApplicationDraftCommandHandler {

  /** Submits an application draft and emits the application-created event. */
  @CommandHandler
  public UUID handle(
      SubmitApplicationDraftCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationDraftStore draftStore,
      ApplicationCreationDetailsFactory detailsFactory,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationDraftPayload draft =
        ApplicationCommandHandlerSupport.requireDraft(command.applicationId(), draftStore);
    ApplicationCreationDetails details =
        detailsFactory.prepare(
            draft.status(),
            draft.laaReference(),
            draft.applicationContent(),
            draft.serialisedRequest(),
            application.getState().getSchemaVersion(),
            draft.potentialDuplicates());
    long applicationDataVersion = 0L;
    ApplicationDataPayload payload = ApplicationDataPayload.from(details);
    for (var filename : draft.documentFilenames().entrySet()) {
      payload = payload.withDocumentFilename(filename.getKey(), filename.getValue());
    }
    String fingerprint =
        applicationDataStore.append(
            command.applicationId(),
            applicationDataVersion,
            payload,
            details.serialisedRequest(),
            details.occurredAt());
    eventAppender.append(
        ApplicationDecider.decideSubmitDraft(
            command.applicationId(), applicationDataVersion, fingerprint, details));
    draftStore.delete(command.applicationId());
    return command.applicationId();
  }
}
