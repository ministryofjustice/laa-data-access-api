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
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftContent;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/** Handles submission of application drafts. */
@Component
public class SubmitApplicationDraftCommandHandler {

  /** Submits a draft, read from its events, and emits the application-created event. */
  @CommandHandler
  public UUID handle(
      SubmitApplicationDraftCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationCreationDetailsFactory detailsFactory,
      EventAppender eventAppender) {
    ApplicationDraftContent draft =
        ApplicationCommandHandlerSupport.requireDraft(application, command.applicationId());
    ApplicationCreationDetails details =
        detailsFactory.prepare(
            draft.status(),
            draft.laaReference(),
            draft.applicationContent(),
            draft.serialisedRequest(),
            application.getState().getSchemaVersion(),
            draft.potentialDuplicates());
    eventAppender.append(
        ApplicationDecider.decideSubmitDraft(
            command.applicationId(),
            0L,
            PayloadFingerprint.compute(details.serialisedRequest()),
            details));
    return command.applicationId();
  }
}
