package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import java.util.List;
import java.util.Optional;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.AddApplicationToLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ChangeLinkedGroupLeadCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.EstablishLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.RemoveApplicationFromLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignLinkedGroupWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.DirectWorkItemUnassignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignLinkedGroupWorkItemCommand;

/** Rejects linked-group commands dispatched without the resolver's route locks. */
@Component
@NullMarked
@ConditionalOnProperty(
    name = "dstew.linked-group.route-lock-guard.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RouteLockGuardDispatchInterceptor
    implements MessageDispatchInterceptor<CommandMessage> {

  /** Verifies route locks before allowing a linked-group command to continue. */
  @Override
  public MessageStream<?> interceptOnDispatch(
      CommandMessage message,
      @Nullable ProcessingContext context,
      MessageDispatchInterceptorChain<CommandMessage> chain) {
    var violation = violation(message.payload());
    if (violation.isPresent()) {
      return MessageStream.failed(new IllegalStateException(violation.get()));
    }
    return chain.proceed(message, context);
  }

  private Optional<String> violation(Object payload) {
    return switch (payload) {
      case EstablishLinkedApplicationGroupCommand command ->
          RouteLockRegistry.missingApplications(command.memberApplicationIds()).isEmpty()
              ? Optional.empty()
              : Optional.of(message(payload, command.memberApplicationIds().toString()));
      case AddApplicationToLinkedGroupCommand command ->
          RouteLockRegistry.holdsGroup(command.groupId())
                  && RouteLockRegistry.missingApplications(List.of(command.applicationId()))
                      .isEmpty()
              ? Optional.empty()
              : Optional.of(
                  message(
                      payload,
                      "group "
                          + command.groupId()
                          + " and application "
                          + command.applicationId()));
      case RemoveApplicationFromLinkedGroupCommand command ->
          RouteLockRegistry.holdsGroup(command.groupId())
              ? Optional.empty()
              : Optional.of(message(payload, "group " + command.groupId()));
      case ChangeLinkedGroupLeadCommand command ->
          RouteLockRegistry.holdsGroup(command.groupId())
              ? Optional.empty()
              : Optional.of(message(payload, "group " + command.groupId()));
      case AssignLinkedGroupWorkItemCommand command ->
          RouteLockRegistry.holdsGroup(command.groupId())
              ? Optional.empty()
              : Optional.of(message(payload, "group " + command.groupId()));
      case UnassignLinkedGroupWorkItemCommand command ->
          RouteLockRegistry.holdsGroup(command.groupId())
              ? Optional.empty()
              : Optional.of(message(payload, "group " + command.groupId()));
      case DirectWorkItemAssignmentCommand command ->
          RouteLockRegistry.missingApplications(List.of(command.workItemId())).isEmpty()
              ? Optional.empty()
              : Optional.of(message(payload, List.of(command.workItemId()).toString()));
      case DirectWorkItemUnassignmentCommand command ->
          RouteLockRegistry.missingApplications(List.of(command.workItemId())).isEmpty()
              ? Optional.empty()
              : Optional.of(message(payload, List.of(command.workItemId()).toString()));
      default -> Optional.empty();
    };
  }

  private String message(Object payload, String lockTarget) {
    return payload.getClass().getSimpleName()
        + " requires linked-group route locks for "
        + lockTarget
        + "; acquire them through ApplicationGroupRouteResolver before dispatching";
  }
}
