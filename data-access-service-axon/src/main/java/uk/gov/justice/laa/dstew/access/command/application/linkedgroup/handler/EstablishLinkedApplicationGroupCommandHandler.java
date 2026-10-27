package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.EstablishLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupAssignmentDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupMembers;

/** Handles establishing a linked application group. */
@Component
public class EstablishLinkedApplicationGroupCommandHandler {

  /** Establishes the group idempotently when the routed group stream is empty. */
  @CommandHandler
  public void handle(
      EstablishLinkedApplicationGroupCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      StateManager stateManager,
      ProcessingContext context,
      EventAppender eventAppender) {
    var created =
        LinkedApplicationGroupDecider.decideEstablish(
            group == null ? new LinkedApplicationGroupState() : group.getState(), command);
    if (created.isEmpty()) {
      return;
    }

    var applications =
        LinkedGroupMembers.load(stateManager, command.memberApplicationIds(), context);
    var lead = applications.get(command.leadApplicationId());
    var joining =
        applications.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(command.leadApplicationId()))
            .map(Map.Entry::getValue)
            .toList();
    var events = new ArrayList<Object>();
    events.add(created.get());
    events.addAll(
        LinkedGroupAssignmentDecider.decideJoin(
            command.groupId(), List.of(lead), joining, command.occurredAt()));
    eventAppender.append(events);
  }
}
