package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.decision.RecordAutoGrantedOutcomeCommand;
import uk.gov.justice.laa.dstew.access.exception.ApplicationAutoGrantOutcomeConflictException;

/** Handles recording automatic-grant outcomes. */
@Component
public class RecordAutoGrantedOutcomeCommandHandler {

  /** Records the automatic-grant outcome if it has not already been persisted. */
  @CommandHandler
  public void handle(
      RecordAutoGrantedOutcomeCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireApplicationExists(application, command.applicationId());
    validateAutomaticOutcome(command, application, applicationDataStore);
    if (application.getState().getAutoGranted() == AutoGrantedState.AUTOGRANTED) {
      return;
    }

    var current =
        applicationDataStore.get(
            command.applicationId(), application.getState().getApplicationDataVersion());
    MakeApplicationDecisionCommand decision = automaticGrantDecision(command, application, current);
    MakeApplicationDecisionCommandHandler.recordDecision(
        decision,
        AutoGrantedState.AUTOGRANTED,
        current,
        application,
        applicationDataStore,
        eventAppender);
  }

  private static void validateAutomaticOutcome(
      RecordAutoGrantedOutcomeCommand command,
      ApplicationAggregate application,
      ApplicationDataStore applicationDataStore) {
    ApplicationState state = application.getState();
    if (state.getAutoGranted() == AutoGrantedState.MANUAL) {
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
    if (state.getAutoGranted() != AutoGrantedState.AUTOGRANTED) {
      return;
    }
    var recorded =
        applicationDataStore.get(command.applicationId(), state.getApplicationDataVersion());
    if (!java.util.Objects.equals(
        recorded.decisionSerialisedRequest(), command.serialisedRequest())) {
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
  }

  private static MakeApplicationDecisionCommand automaticGrantDecision(
      RecordAutoGrantedOutcomeCommand command,
      ApplicationAggregate application,
      ApplicationDataPayload current) {
    var grantedProceedings =
        current.proceedings().stream()
            .map(
                proceeding ->
                    new MakeDecisionProceeding(
                        proceeding.getId(), DecisionValue.GRANTED.name(), null, "Autogranted"))
            .toList();
    return new MakeApplicationDecisionCommand(
        command.applicationId(),
        null,
        application.getState().getApplicationVersion(),
        DecisionValue.GRANTED.name(),
        grantedProceedings,
        command.certificate(),
        command.serialisedRequest(),
        "Autogranted",
        command.occurredAt());
  }
}
