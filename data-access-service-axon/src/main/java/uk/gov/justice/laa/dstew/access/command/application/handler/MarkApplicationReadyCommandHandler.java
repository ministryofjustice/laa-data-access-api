package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;

/** Handles marking applications ready for manual assessment. */
@Component
public class MarkApplicationReadyCommandHandler {

  /** Stores the manual-assessment marker if not already recorded. */
  @CommandHandler
  public ReadyApplicationResult handle(
      MarkApplicationReadyCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireApplicationExists(application, command.applicationId());
    ReadyApplicationResult result = ApplicationDecider.decideReady(application.getState(), command);
    if (result == ReadyApplicationResult.ALREADY_RECORDED) {
      return result;
    }

    var current =
        applicationDataStore.get(
            command.applicationId(), application.getState().getApplicationDataVersion());
    long nextApplicationVersion = application.getState().getApplicationVersion() + 1;
    long nextDataVersion = application.getState().getApplicationDataVersion() + 1;
    applicationDataStore.append(
        command.applicationId(),
        nextDataVersion,
        current.withManualAssessmentRequired(),
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        new ApplicationReadyForManualAssessmentEvent(
            command.applicationId(),
            nextApplicationVersion,
            nextDataVersion,
            command.occurredAt()));
    return result;
  }
}
