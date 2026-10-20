package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.axonframework.modelling.repository.ManagedEntity;
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
      @InjectEntity(idProperty = "applicationId")
          ManagedEntity<UUID, ApplicationAggregate> managedApplication,
      ApplicationDataStore applicationDataStore,
      ApplicationUpdateDetailsFactory detailsFactory,
      EventAppender eventAppender) {
    ApplicationAggregate application =
        ApplicationCommandHandlerSupport.requireApplicationExists(
            managedApplication, command.applicationId());
    var current =
        applicationDataStore.get(
            command.applicationId(), application.getState().getApplicationDataVersion());
    String nextStatus =
        command.status() == null ? application.getState().getStatus() : command.status();
    boolean enteringSubmitted =
        !"APPLICATION_SUBMITTED".equals(application.getState().getStatus())
            && "APPLICATION_SUBMITTED".equals(nextStatus);
    var updated = detailsFactory.prepare(command, current, enteringSubmitted);
    var event = ApplicationDecider.decideUpdate(application.getState(), command);
    applicationDataStore.append(
        command.applicationId(),
        event.applicationDataVersion(),
        updated,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(event);
  }
}
