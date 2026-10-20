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
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;

/** Handles application note creation. */
@Component
public class CreateNoteCommandHandler {

  /** Appends a note to the application's immutable data. */
  @CommandHandler
  public void handle(
      CreateNoteCommand command,
      @InjectEntity(idProperty = "applicationId")
          ManagedEntity<UUID, ApplicationAggregate> managedApplication,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationAggregate application =
        ApplicationCommandHandlerSupport.requireApplicationExists(
            managedApplication, command.applicationId());
    var event = ApplicationDecider.decideNote(application.getState(), command);
    var current =
        applicationDataStore.get(
            command.applicationId(), application.getState().getApplicationDataVersion());
    applicationDataStore.append(
        command.applicationId(),
        event.applicationDataVersion(),
        current.withNote(command.noteText(), command.occurredAt()),
        command.serialisedNoteRequest(),
        command.occurredAt());
    eventAppender.append(event);
  }
}
