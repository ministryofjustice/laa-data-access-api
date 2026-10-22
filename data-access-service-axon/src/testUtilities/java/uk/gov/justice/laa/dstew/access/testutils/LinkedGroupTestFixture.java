package uk.gov.justice.laa.dstew.access.testutils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.test.fixture.AxonTestFixture;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegatedAssignment;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.AddApplicationToLinkedGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.AssignLinkedGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.ChangeLinkedGroupLeadCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.EstablishLinkedApplicationGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.InheritLinkedGroupAssignmentCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.RemoveApplicationFromLinkedGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.UnassignLinkedGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.ValidateLinkedGroupAssigneeCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.ValidatePriorAuthorityGroupEligibilityCommandHandler;

/** Builds Axon test fixtures for the linked-group aggregate and its dedicated command handlers. */
public final class LinkedGroupTestFixture {

  private LinkedGroupTestFixture() {}

  /**
   * Builds neutral delegated-assignment values for each distinct non-null application identifier.
   */
  public static Map<UUID, DelegatedAssignment> unassignedDelegations(List<UUID> applicationIds) {
    return applicationIds.stream()
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toMap(Function.identity(), ignored -> new DelegatedAssignment(null)));
  }

  /** Creates a fixture with the group entity and all of its dedicated command handlers. */
  public static AxonTestFixture linkedGroupFixture() {
    return AxonTestFixture.with(
        EventSourcingConfigurer.create()
            .registerEntity(
                EventSourcedEntityModule.autodetected(
                    UUID.class, LinkedApplicationGroupAggregate.class))
            .registerCommandHandlingModule(
                CommandHandlingModule.named("linked-group-command-handlers")
                    .commandHandlers(
                        handlers ->
                            handlers
                                .autodetectedCommandHandlingComponent(
                                    configuration ->
                                        new EstablishLinkedApplicationGroupCommandHandler())
                                .autodetectedCommandHandlingComponent(
                                    configuration ->
                                        new AddApplicationToLinkedGroupCommandHandler())
                                .autodetectedCommandHandlingComponent(
                                    configuration -> new ChangeLinkedGroupLeadCommandHandler())
                                .autodetectedCommandHandlingComponent(
                                    configuration ->
                                        new RemoveApplicationFromLinkedGroupCommandHandler())
                                .autodetectedCommandHandlingComponent(
                                    configuration ->
                                        new ValidatePriorAuthorityGroupEligibilityCommandHandler())
                                .autodetectedCommandHandlingComponent(
                                    configuration -> new AssignLinkedGroupCommandHandler())
                                .autodetectedCommandHandlingComponent(
                                    configuration -> new UnassignLinkedGroupCommandHandler())
                                .autodetectedCommandHandlingComponent(
                                    configuration ->
                                        new InheritLinkedGroupAssignmentCommandHandler())
                                .autodetectedCommandHandlingComponent(
                                    configuration ->
                                        new ValidateLinkedGroupAssigneeCommandHandler()))));
  }
}
