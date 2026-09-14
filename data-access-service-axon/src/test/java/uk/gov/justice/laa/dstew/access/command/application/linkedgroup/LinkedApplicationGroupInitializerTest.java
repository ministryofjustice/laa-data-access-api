package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupInitializerTest {

  @Mock private CommandGateway commandGateway;

  @InjectMocks private LinkedApplicationGroupInitializer initializer;

  private UUID groupId;
  private UUID leadApplicationId;
  private UUID memberApplicationId;
  private Instant occurredAt;

  @BeforeEach
  void setUp() {
    groupId = UUID.randomUUID();
    leadApplicationId = UUID.randomUUID();
    memberApplicationId = UUID.randomUUID();
    occurredAt = Instant.parse("2026-07-15T08:00:00Z");
  }

  @Test
  void givenLeadOnlyGroupRequested_whenHandled_thenDispatchesEstablishCommand() {
    initializer.on(
        new LinkedApplicationGroupRequested(
            groupId, leadApplicationId, List.of(leadApplicationId), occurredAt));

    verify(commandGateway)
        .sendAndWait(
            new EstablishLinkedApplicationGroupCommand(
                groupId, leadApplicationId, List.of(leadApplicationId), occurredAt));
  }

  @Test
  void givenExistingGroupRequested_whenHandled_thenDispatchesAddCommand() {
    initializer.on(
        new LinkedApplicationGroupRequested(
            groupId,
            leadApplicationId,
            List.of(leadApplicationId, memberApplicationId),
            occurredAt));

    verify(commandGateway)
        .sendAndWait(
            new AddApplicationToLinkedGroupCommand(groupId, memberApplicationId, occurredAt));
    verify(commandGateway, never())
        .sendAndWait(
            new EstablishLinkedApplicationGroupCommand(
                groupId,
                leadApplicationId,
                List.of(leadApplicationId, memberApplicationId),
                occurredAt));
  }

  @Test
  void
      givenRequestedGroupWithoutLeadMember_whenHandled_thenSkipsAddAndPropagatesValidationFailure() {
    UUID firstMemberApplicationId = UUID.randomUUID();
    UUID secondMemberApplicationId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                initializer.on(
                    new LinkedApplicationGroupRequested(
                        groupId,
                        leadApplicationId,
                        List.of(firstMemberApplicationId, secondMemberApplicationId),
                        occurredAt)))
        .isInstanceOf(IllegalArgumentException.class);

    verify(commandGateway, never())
        .sendAndWait(
            new AddApplicationToLinkedGroupCommand(groupId, firstMemberApplicationId, occurredAt));
    verify(commandGateway, never())
        .sendAndWait(
            new AddApplicationToLinkedGroupCommand(groupId, secondMemberApplicationId, occurredAt));
  }

  @Test
  void givenNullLeadApplicationId_whenHandled_thenSkipsAddAndPropagatesValidationFailure() {
    assertThatThrownBy(
            () ->
                initializer.on(
                    new LinkedApplicationGroupRequested(
                        groupId,
                        null,
                        List.of(memberApplicationId, UUID.randomUUID()),
                        occurredAt)))
        .isInstanceOf(NullPointerException.class);

    verify(commandGateway, never())
        .sendAndWait(
            new AddApplicationToLinkedGroupCommand(groupId, memberApplicationId, occurredAt));
  }

  @Test
  void givenUnestablishedGroupRequested_whenAddRejected_thenFallsBackToEstablishCommand() {
    when(commandGateway.sendAndWait(
            eq(new AddApplicationToLinkedGroupCommand(groupId, memberApplicationId, occurredAt))))
        .thenThrow(new IllegalStateException("Linked application group not established"));

    initializer.on(
        new LinkedApplicationGroupRequested(
            groupId,
            leadApplicationId,
            List.of(leadApplicationId, memberApplicationId),
            occurredAt));

    verify(commandGateway)
        .sendAndWait(
            new AddApplicationToLinkedGroupCommand(groupId, memberApplicationId, occurredAt));
    verify(commandGateway)
        .sendAndWait(
            new EstablishLinkedApplicationGroupCommand(
                groupId,
                leadApplicationId,
                List.of(leadApplicationId, memberApplicationId),
                occurredAt));
  }
}
