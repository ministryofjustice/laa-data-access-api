package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ChangeLinkedGroupLeadCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupJustificationStore;

/** Handles changing a linked group's lead. */
@Component
@RequiredArgsConstructor
public class ChangeLinkedGroupLeadCommandHandler {
  private final LinkedGroupJustificationStore justificationStore;

  /** Changes the lead of an established group. */
  @CommandHandler
  public void handle(
      ChangeLinkedGroupLeadCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideChangeLead(
            LinkedApplicationGroupAggregate.stateOf(group), command)
        .ifPresent(
            event ->
                eventAppender.append(
                    event.withJustification(
                        justificationStore.save(
                            command.groupId(), command.justification(), command.occurredAt()))));
  }
}
