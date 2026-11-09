package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdateDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationCommand;

/** Handles application updates. */
@Component
public class UpdateApplicationCommandHandler {

  /** Replaces application content and appends the update event. */
  @CommandHandler
  public void handle(
      UpdateApplicationCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationUpdateDetailsFactory detailsFactory,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireContentRetained(application);
    var parsed = detailsFactory.parse(command);
    var event = ApplicationDecider.decideUpdate(application.getState(), command, parsed);
    eventAppender.append(event);
  }
}
