package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
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
      ApplicationDataStore applicationDataStore,
      ApplicationUpdateDetailsFactory detailsFactory,
      EventAppender eventAppender) {
    var current = ApplicationCommandHandlerSupport.currentContent(application);
    var parsed = detailsFactory.parse(command);
    var event = ApplicationDecider.decideUpdate(application.getState(), command, parsed);
    var updated = detailsFactory.prepare(parsed, command, current, event.enteredSubmitted());
    applicationDataStore.append(
        command.applicationId(),
        event.applicationDataVersion(),
        updated,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(event);
  }
}
