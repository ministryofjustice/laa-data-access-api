package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
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
      EventAppender eventAppender) {
    validateAutomaticOutcome(command, application);
    if (application.getState().getAutoGranted() == AutoGrantedState.AUTOGRANTED) {
      return;
    }

    ApplicationCommandHandlerSupport.requireContentRetained(application);
    MakeApplicationDecisionCommand decision = automaticGrantDecision(command, application);
    MakeApplicationDecisionCommandHandler.recordDecision(
        decision, AutoGrantedState.AUTOGRANTED, application, eventAppender);
  }

  private static void validateAutomaticOutcome(
      RecordAutoGrantedOutcomeCommand command, ApplicationAggregate application) {
    ApplicationState state = application.getState();
    if (state.getAutoGranted() == AutoGrantedState.MANUAL) {
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
    if (state.getAutoGranted() != AutoGrantedState.AUTOGRANTED) {
      return;
    }
    if (!ApplicationDecider.isSameAutoGrantRequest(state, command.serialisedRequest())) {
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
  }

  private static MakeApplicationDecisionCommand automaticGrantDecision(
      RecordAutoGrantedOutcomeCommand command, ApplicationAggregate application) {
    var grantedProceedings =
        application.getState().getProceedingIds().stream()
            .map(
                proceedingId ->
                    new MakeDecisionProceeding(
                        proceedingId, DecisionValue.GRANTED.name(), null, "Autogranted"))
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
