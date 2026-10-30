package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import java.util.ArrayList;
import java.util.List;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.AddApplicationToLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupAssignmentDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupMembers;

/** Handles adding an application to an established linked group. */
@Component
public class AddApplicationToLinkedGroupCommandHandler {

  /** Adds one application to an established group idempotently. */
  @CommandHandler
  public void handle(
      AddApplicationToLinkedGroupCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      StateManager stateManager,
      ProcessingContext context,
      EventAppender eventAppender) {
    var added =
        LinkedApplicationGroupDecider.decideAddApplication(
            LinkedApplicationGroupAggregate.stateOf(group), command);
    if (added.isEmpty()) {
      return;
    }

    var memberIds = List.copyOf(group.getState().memberIdsOrEmpty());
    var applicationIdsToLoad = new ArrayList<>(memberIds);
    applicationIdsToLoad.add(command.applicationId());
    var applications = LinkedGroupMembers.load(stateManager, applicationIdsToLoad, context);
    var existing = memberIds.stream().map(applications::get).toList();
    var joining = List.of(applications.get(command.applicationId()));
    var events = new ArrayList<Object>();
    events.add(added.get());
    events.addAll(
        LinkedGroupAssignmentDecider.decideJoin(
            command.groupId(), existing, joining, command.occurredAt()));
    eventAppender.append(events);
  }
}
