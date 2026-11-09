package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerResource;
import uk.gov.justice.laa.dstew.access.security.OfficeCodeResourceWriteAccessResolver;
import uk.gov.justice.laa.dstew.access.security.RequireWriteAccess;
import uk.gov.justice.laa.dstew.access.security.WriteAccessResolverResource;

/** Handles updates to prior-authority drafts. */
@Component
public class UpdatePriorAuthorityDraftCommandHandler {

  /** Updates the prior-authority draft and emits the corresponding event. */
  @CommandHandler
  @RequireWriteAccess(resolver = OfficeCodeResourceWriteAccessResolver.class)
  void handle(
      @AuthenticatedCallerResource UpdatePriorAuthorityDraftCommand command,
      @InjectEntity(idProperty = "priorAuthorityId") @WriteAccessResolverResource
          PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    if (priorAuthority.getState().draft().isEmpty()) {
      throw new ResourceNotFoundException(
          "Prior Authority %s not found".formatted(command.priorAuthorityId()));
    }
    eventAppender.append(
        PriorAuthorityDecider.decideDraftUpdated(command, priorAuthority.getState()));
  }
}
