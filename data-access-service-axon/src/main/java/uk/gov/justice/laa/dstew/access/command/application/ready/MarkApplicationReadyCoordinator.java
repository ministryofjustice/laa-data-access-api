package uk.gov.justice.laa.dstew.access.command.application.ready;

import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;

/** Locks the linked group and dispatches readiness in a fresh transaction for each attempt. */
@Service
@RequiredArgsConstructor
public class MarkApplicationReadyCoordinator {
  private final ApplicationGroupRouteResolver groupRouteResolver;
  private final RetryingCommandDispatcher dispatcher;
  private final CommandGateway commandGateway;
  private final PlatformTransactionManager transactionManager;

  /** Records readiness; a grouped application takes its group's assignment in the same command. */
  public ReadyApplicationResult handle(MarkApplicationReadyCommand command) {
    var transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return dispatcher.dispatch(
        () ->
            transactionTemplate.execute(
                status -> {
                  var groupId = groupRouteResolver.lockForAssignment(command.applicationId());
                  return commandGateway.sendAndWait(
                      command.withLinkedGroupId(groupId.orElse(null)),
                      ReadyApplicationResult.class);
                }));
  }
}
