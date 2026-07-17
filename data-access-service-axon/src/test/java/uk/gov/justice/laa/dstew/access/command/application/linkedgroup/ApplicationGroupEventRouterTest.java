package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;

class ApplicationGroupEventRouterTest {

  private CommandGateway commandGateway;
  private ApplicationGroupEventRouter router;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    commandGateway = mock(CommandGateway.class);
    ObjectProvider<CommandGateway> provider = mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(commandGateway);
    router = new ApplicationGroupEventRouter(provider);
  }

  @Test
  void givenCreatedEventWithLeadId_whenHandled_thenDispatchesCreateLinkedApplicationGroupCommand() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationCreatedEvent event =
        minimalEvent(applicationId, leadApplicationId, Instant.parse("2026-07-15T08:00:00Z"));

    router.on(event);

    verify(commandGateway).sendAndWait(any(CreateLinkedApplicationGroupCommand.class));
  }

  @Test
  void givenCreatedEventWithoutLeadId_whenHandled_thenNoCommandDispatched() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event =
        minimalEvent(applicationId, null, Instant.parse("2026-07-15T08:00:00Z"));

    router.on(event);

    verifyNoInteractions(commandGateway);
  }

  @Test
  void givenLinkedApplicationGroupRequested_whenHandled_thenDispatchesInitialiseCommand() {
    UUID groupId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    List<UUID> members = List.of(leadApplicationId, UUID.randomUUID());
    LinkedApplicationGroupRequested event =
        new LinkedApplicationGroupRequested(
            groupId, leadApplicationId, members, "{}", Instant.parse("2026-07-15T08:00:00Z"));

    router.on(event);

    verify(commandGateway).sendAndWait(any(InitialiseLinkedApplicationGroupCommand.class));
  }

  private ApplicationCreatedEvent minimalEvent(
      UUID applicationId, UUID leadApplicationId, Instant occurredAt) {
    ApplicationContent content = ApplicationContent.builder().build(); // empty — no linked apps
    return new ApplicationCreatedEvent(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        content,
        List.of(),
        1,
        "APPLY",
        applicationId,
        occurredAt,
        null,
        false,
        null,
        null,
        List.of(),
        "{}",
        occurredAt,
        leadApplicationId);
  }
}
