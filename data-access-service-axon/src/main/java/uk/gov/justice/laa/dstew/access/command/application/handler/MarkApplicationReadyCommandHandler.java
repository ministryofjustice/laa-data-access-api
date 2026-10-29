package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.ArrayList;
import java.util.List;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupAssignmentDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupMembers;
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
      EventAppender eventAppender,
      StateManager stateManager,
      ProcessingContext context) {
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
    List<Object> events = new ArrayList<>();
    events.add(
        new ApplicationReadyForManualAssessmentEvent(
            command.applicationId(),
            nextApplicationVersion,
            nextDataVersion,
            command.occurredAt()));
    if (command.linkedGroupId() != null) {
      var group =
          stateManager
              .loadEntity(LinkedApplicationGroupAggregate.class, command.linkedGroupId(), context)
              .join()
              .getState();
      var others =
          LinkedGroupMembers.load(
              stateManager,
              group.memberIdsOrEmpty().stream()
                  .filter(id -> !id.equals(command.applicationId()))
                  .toList(),
              context);
      LinkedGroupAssignmentDecider.decideReadyAssignment(
              group,
              application.getState(),
              nextApplicationVersion,
              others.values(),
              command.occurredAt())
          .ifPresent(events::add);
    }
    eventAppender.append(events);
    return result;
  }
}
