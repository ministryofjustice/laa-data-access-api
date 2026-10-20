package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.axonframework.modelling.repository.ManagedEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;

/** Handles validation that an application has been granted. */
@Component
public class ValidateApplicationGrantedCommandHandler {

  /** Validates that the application has an overall decision of granted. */
  @CommandHandler
  public void handle(
      ValidateApplicationGrantedCommand command,
      @InjectEntity(idProperty = "applicationId")
          ManagedEntity<UUID, ApplicationAggregate> managedApplication) {
    ApplicationAggregate application =
        ApplicationCommandHandlerSupport.requireApplicationExists(
            managedApplication, command.applicationId());
    ApplicationDecider.validateGranted(application.getState());
  }
}
