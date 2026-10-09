package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.HashMap;
import java.util.UUID;
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
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;

/** Handles manual application decisions. */
@Component
public class MakeApplicationDecisionCommandHandler {

  /** Validates and records a manual decision for an application. */
  @CommandHandler
  public void handle(
      MakeApplicationDecisionCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireApplicationExists(application, command.applicationId());
    ApplicationState state = application.getState();
    uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider
        .validateManualDecisionAssignment(state, command);
    var current =
        applicationDataStore.get(command.applicationId(), state.getApplicationDataVersion());
    recordDecision(
        command,
        AutoGrantedState.MANUAL,
        current,
        application,
        applicationDataStore,
        eventAppender);
  }

  static void recordDecision(
      MakeApplicationDecisionCommand command,
      AutoGrantedState autoGranted,
      ApplicationDataPayload current,
      ApplicationAggregate application,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationState state = application.getState();
    ApplicationDecisionMadeEvent decision =
        uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider.decideDecision(
            state, command, current, autoGranted);
    long nextVersion = state.getApplicationDataVersion() + 1;
    var updated =
        current.withDecision(
            command.overallDecision(),
            autoGranted,
            meritsDecisions(current, command),
            DecisionValue.GRANTED.name().equals(command.overallDecision())
                ? command.certificate()
                : null,
            command.serialisedRequest(),
            command.eventDescription());
    applicationDataStore.append(
        command.applicationId(),
        nextVersion,
        updated,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(decision);
  }

  private static HashMap<UUID, ApplicationMeritsDecision> meritsDecisions(
      ApplicationDataPayload current, MakeApplicationDecisionCommand command) {
    var meritsDecisions =
        new HashMap<>(
            current.meritsDecisions() == null ? java.util.Map.of() : current.meritsDecisions());
    command
        .proceedings()
        .forEach(
            proceeding ->
                meritsDecisions.put(
                    proceeding.proceedingId(),
                    new ApplicationMeritsDecision(
                        proceeding.decision(), proceeding.reason(), proceeding.justification())));
    return meritsDecisions;
  }
}
