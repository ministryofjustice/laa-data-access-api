package uk.gov.justice.laa.dstew.access.command.application.ready;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;

/** Locks the linked group and dispatches readiness in a fresh transaction for each attempt. */
@Service
public class MarkApplicationReadyCoordinator {
  private final ApplicationGroupRouteResolver groupRouteResolver;
  private final RetryingCommandDispatcher dispatcher;
  private final CommandGateway commandGateway;
  private final TransactionTemplate transactionTemplate;

  /** Creates the coordinator with a new transaction for each complete attempt. */
  public MarkApplicationReadyCoordinator(
      ApplicationGroupRouteResolver groupRouteResolver,
      RetryingCommandDispatcher dispatcher,
      CommandGateway commandGateway,
      PlatformTransactionManager transactionManager) {
    this.groupRouteResolver = groupRouteResolver;
    this.dispatcher = dispatcher;
    this.commandGateway = commandGateway;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Records readiness; a grouped application takes its group's assignment in the same command. */
  public ReadyApplicationResult handle(MarkApplicationReadyCommand command) {
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
