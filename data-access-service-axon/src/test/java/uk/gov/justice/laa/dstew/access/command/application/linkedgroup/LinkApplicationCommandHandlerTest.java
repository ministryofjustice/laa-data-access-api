package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkAction;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkPlan;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class LinkApplicationCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-14T15:00:00Z");

  @Mock private ApplicationGroupRouteResolver routeResolver;
  @Mock private RetryingCommandDispatcher dispatcher;

  @InjectMocks private LinkApplicationCommandHandler handler;

  @Captor private ArgumentCaptor<Object> dispatchedCommandCaptor;

  private UUID sourceApplicationId;
  private UUID targetApplicationId;

  @BeforeEach
  void setUp() {
    sourceApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000101");
    targetApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000202");
  }

  @Test
  void rejectsSelfLinkBeforeRouteLookup() {
    var command =
        new LinkApplicationCommand(
            sourceApplicationId, sourceApplicationId, LinkType.FAMILY, OCCURRED_AT);

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly("Source and target application IDs must be different"));

    verifyNoInteractions(routeResolver, dispatcher);
  }

  @Test
  void rejectsNullLinkTypeBeforeRouteLookup() {
    var command =
        new LinkApplicationCommand(sourceApplicationId, targetApplicationId, null, OCCURRED_AT);

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors()).containsExactly("linkType: must not be null"));

    verifyNoInteractions(routeResolver, dispatcher);
  }

  @Test
  void whenApplicationsAreAlreadyLinked_thenDispatchesNoAggregateCommand() {
    UUID existingGroupId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(new ApplicationLinkPlan(ApplicationLinkAction.ALREADY_LINKED, existingGroupId));

    handler.handle(linkCommand());

    verify(routeResolver).resolve(sourceApplicationId, targetApplicationId);
    verifyNoInteractions(dispatcher);
  }

  @Test
  void whenPlanCreatesGroup_thenDispatchesEstablishCommandWithRandomGroupIdentifier() {
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(new ApplicationLinkPlan(ApplicationLinkAction.CREATE_GROUP, null));

    handler.handle(linkCommand());

    verify(dispatcher).dispatch(dispatchedCommandCaptor.capture());
    assertThat(dispatchedCommandCaptor.getValue())
        .isInstanceOfSatisfying(
            EstablishLinkedApplicationGroupCommand.class,
            dispatchedCommand -> {
              assertThat(dispatchedCommand.groupId()).isNotNull();
              assertThat(dispatchedCommand.groupId())
                  .isNotEqualTo(sourceApplicationId)
                  .isNotEqualTo(targetApplicationId);
              assertThat(dispatchedCommand.leadApplicationId()).isEqualTo(targetApplicationId);
              assertThat(dispatchedCommand.memberApplicationIds())
                  .containsExactly(targetApplicationId, sourceApplicationId);
              assertThat(dispatchedCommand.occurredAt()).isEqualTo(OCCURRED_AT);
            });
  }

  @Test
  void whenPlanAddsToExistingGroup_thenDispatchesAddCommand() {
    UUID existingGroupId = UUID.fromString("10000000-0000-0000-0000-000000000002");
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(
            new ApplicationLinkPlan(ApplicationLinkAction.ADD_TO_EXISTING_GROUP, existingGroupId));

    handler.handle(linkCommand());

    verify(dispatcher).dispatch(dispatchedCommandCaptor.capture());
    assertThat(dispatchedCommandCaptor.getValue())
        .isEqualTo(
            new AddApplicationToLinkedGroupCommand(
                existingGroupId, sourceApplicationId, OCCURRED_AT));
  }

  @Test
  void propagatesResolverNotFoundExceptionUnchanged() {
    var exception =
        new ResourceNotFoundException(
            "No application group route found for target application " + targetApplicationId);
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId)).thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(linkCommand())).isSameAs(exception);

    verifyNoInteractions(dispatcher);
  }

  @Test
  void propagatesResolverConflictExceptionUnchanged() {
    var exception =
        new ApplicationLinkConflictException(
            "Application " + sourceApplicationId + " already belongs to a different linked group");
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId)).thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(linkCommand())).isSameAs(exception);

    verifyNoInteractions(dispatcher);
  }

  private LinkApplicationCommand linkCommand() {
    return new LinkApplicationCommand(
        sourceApplicationId, targetApplicationId, LinkType.FAMILY, OCCURRED_AT);
  }
}
