package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;

/** Handles application note creation. */
@Component
public class CreateNoteCommandHandler {

  /** Records a note against the application. */
  @CommandHandler
  public void handle(
      CreateNoteCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      EventAppender eventAppender) {
    var event = ApplicationDecider.decideNote(application.getState(), command);
    ApplicationCommandHandlerSupport.requireContentRetained(application);
    eventAppender.append(event);
  }
}
