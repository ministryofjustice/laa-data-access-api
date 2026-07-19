package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication;
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

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(commandGateway).sendAndWait(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(
            new CreateLinkedApplicationGroupCommand(
                leadApplicationId,
                applicationId,
                List.of(leadApplicationId, applicationId),
                "{}",
                event.occurredAt()));
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

    verify(commandGateway)
        .sendAndWait(
            new InitialiseLinkedApplicationGroupCommand(
                groupId, leadApplicationId, members, "{}", event.occurredAt()));
  }

  @Test
  void givenOtherAssociatedApplications_whenHandled_thenValidatesDistinctIdsBeforeCreatingGroup() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    UUID otherAssociatedId = UUID.randomUUID();
    ApplicationContent content =
        ApplicationContent.builder()
            .allLinkedApplications(
                List.of(
                    linked(leadApplicationId, applicationId),
                    linked(leadApplicationId, otherAssociatedId),
                    linked(leadApplicationId, otherAssociatedId),
                    linked(leadApplicationId, leadApplicationId)))
            .build();
    ApplicationCreatedEvent event =
        event(applicationId, leadApplicationId, content, Instant.parse("2026-07-15T08:00:00Z"));

    router.on(event);

    InOrder order = inOrder(commandGateway);
    order
        .verify(commandGateway)
        .sendAndWait(new ValidateApplicationExistsCommand(otherAssociatedId));
    order
        .verify(commandGateway)
        .sendAndWait(
            new CreateLinkedApplicationGroupCommand(
                leadApplicationId,
                applicationId,
                List.of(leadApplicationId, applicationId),
                "{}",
                event.occurredAt()));
    order.verifyNoMoreInteractions();
  }

  @Test
  void givenAssociatedApplicationValidationFails_whenHandled_thenDoesNotCreateGroup() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    UUID missingAssociatedId = UUID.randomUUID();
    ApplicationContent content =
        ApplicationContent.builder()
            .allLinkedApplications(List.of(linked(leadApplicationId, missingAssociatedId)))
            .build();
    ApplicationCreatedEvent event =
        event(applicationId, leadApplicationId, content, Instant.parse("2026-07-15T08:00:00Z"));
    RuntimeException failure = new RuntimeException("missing associated application");
    when(commandGateway.sendAndWait(eq(new ValidateApplicationExistsCommand(missingAssociatedId))))
        .thenThrow(failure);

    assertThatThrownBy(() -> router.on(event)).isSameAs(failure);

    verify(commandGateway, never())
        .sendAndWait(
            new CreateLinkedApplicationGroupCommand(
                leadApplicationId,
                applicationId,
                List.of(leadApplicationId, applicationId),
                "{}",
                event.occurredAt()));
  }

  private ApplicationCreatedEvent minimalEvent(
      UUID applicationId, UUID leadApplicationId, Instant occurredAt) {
    ApplicationContent content = ApplicationContent.builder().build(); // empty — no linked apps
    return event(applicationId, leadApplicationId, content, occurredAt);
  }

  private ApplicationCreatedEvent event(
      UUID applicationId, UUID leadApplicationId, ApplicationContent content, Instant occurredAt) {
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

  private LinkedApplication linked(UUID leadApplicationId, UUID associatedApplicationId) {
    return LinkedApplication.builder()
        .leadApplicationId(leadApplicationId)
        .associatedApplicationId(associatedApplicationId)
        .build();
  }
}
