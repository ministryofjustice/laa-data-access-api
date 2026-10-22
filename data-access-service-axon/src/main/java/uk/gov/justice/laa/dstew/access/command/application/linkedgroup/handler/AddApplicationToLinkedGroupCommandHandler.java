package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.AddApplicationToLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupState;

/** Handles adding an application to an established linked group. */
@Component
public class AddApplicationToLinkedGroupCommandHandler {

  /** Adds one application to an established group idempotently. */
  @CommandHandler
  public void handle(
      AddApplicationToLinkedGroupCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideAddApplication(
            group == null ? new LinkedApplicationGroupState() : group.getState(), command)
        .ifPresent(eventAppender::append);
  }
}
