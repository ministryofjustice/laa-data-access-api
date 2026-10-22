package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.EstablishLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupState;

/** Handles establishing a linked application group. */
@Component
public class EstablishLinkedApplicationGroupCommandHandler {

  /** Establishes the group idempotently when the routed group stream is empty. */
  @CommandHandler
  public void handle(
      EstablishLinkedApplicationGroupCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideEstablish(
            group == null ? new LinkedApplicationGroupState() : group.getState(), command)
        .ifPresent(eventAppender::append);
  }
}
