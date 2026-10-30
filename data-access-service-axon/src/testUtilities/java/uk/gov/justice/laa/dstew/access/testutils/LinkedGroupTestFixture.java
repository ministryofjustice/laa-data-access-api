package uk.gov.justice.laa.dstew.access.testutils;

import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.test.fixture.AxonTestFixture;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.AddApplicationToLinkedGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.ChangeLinkedGroupLeadCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.EstablishLinkedApplicationGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.RemoveApplicationFromLinkedGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.ValidatePriorAuthorityGroupEligibilityCommandHandler;

/** Builds Axon test fixtures for the linked-group aggregate and its dedicated command handlers. */
public final class LinkedGroupTestFixture {

  private LinkedGroupTestFixture() {}

  /** Creates a fixture with the group entity and all of its dedicated command handlers. */
  public static AxonTestFixture linkedGroupFixture() {
    return AxonTestFixture.with(
        EventSourcingConfigurer.create()
            .registerEntity(
                EventSourcedEntityModule.autodetected(
                    UUID.class, LinkedApplicationGroupAggregate.class))
            .registerEntity(
                EventSourcedEntityModule.autodetected(UUID.class, ApplicationAggregate.class))
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
                                        new ValidatePriorAuthorityGroupEligibilityCommandHandler()))));
  }
}
