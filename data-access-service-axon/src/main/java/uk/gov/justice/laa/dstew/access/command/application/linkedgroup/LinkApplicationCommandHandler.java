package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkPlan;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Secured transactional coordinator for explicit application-link commands. */
@Service
@RequiredArgsConstructor
public class LinkApplicationCommandHandler {
  private final ApplicationGroupRouteResolver routeResolver;
  private final RetryingCommandDispatcher dispatcher;

  /** Validates, routes, and dispatches the explicit link command inside one transaction. */
  @Transactional
  @AllowApiCaseworker
  public void handle(LinkApplicationCommand command) {
    validate(command);
    handleLinkType(command);
  }

  @ExcludeFromGeneratedCodeCoverage
  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  private void handleLinkType(LinkApplicationCommand command) {
    Runnable linkHandler =
        switch (command.linkType()) {
          case FAMILY -> () -> handleFamilyLink(command);
        };
    linkHandler.run();
  }

  private void handleFamilyLink(LinkApplicationCommand command) {
    var plan = routeResolver.resolve(command.sourceApplicationId(), command.targetApplicationId());
    planAction(command, plan).run();
  }

  @ExcludeFromGeneratedCodeCoverage
  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  private Runnable planAction(LinkApplicationCommand command, ApplicationLinkPlan plan) {
    return switch (plan.action()) {
      case CREATE_GROUP ->
          () ->
              dispatcher.dispatch(
                  new EstablishLinkedApplicationGroupCommand(
                      UUID.randomUUID(),
                      command.targetApplicationId(),
                      List.of(command.targetApplicationId(), command.sourceApplicationId()),
                      command.occurredAt()));
      case ADD_TO_EXISTING_GROUP ->
          () ->
              dispatcher.dispatch(
                  new AddApplicationToLinkedGroupCommand(
                      plan.groupId(), command.sourceApplicationId(), command.occurredAt()));
      case ALREADY_LINKED ->
          () -> {
            // Idempotent success.
          };
    };
  }

  private void validate(LinkApplicationCommand command) {
    if (command.linkType() == null) {
      throw new ValidationException(List.of("linkType: must not be null"));
    }
    if (command.sourceApplicationId().equals(command.targetApplicationId())) {
      throw new ValidationException(List.of("Source and target application IDs must be different"));
    }
  }
}
