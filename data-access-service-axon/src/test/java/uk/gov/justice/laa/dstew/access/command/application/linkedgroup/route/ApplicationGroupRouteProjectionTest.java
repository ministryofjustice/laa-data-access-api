package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ApplicationGroupRouteProjectionTest {
  private static final Instant ORIGINAL_OCCURRED_AT = Instant.parse("2026-09-14T09:00:00Z");
  private static final Instant ROUTED_AT = Instant.parse("2026-09-14T10:15:00Z");

  @Mock private ApplicationGroupRouteRepository routes;

  @InjectMocks private ApplicationGroupRouteProjection projection;

  @Test
  void createsAStandaloneRouteWhenAnApplicationIsCreated() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId, ROUTED_AT);
    when(routes.findById(applicationId)).thenReturn(Optional.empty());

    projection.on(event);

    ArgumentCaptor<ApplicationGroupRoute> savedRouteCaptor =
        ArgumentCaptor.forClass(ApplicationGroupRoute.class);
    verify(routes).save(savedRouteCaptor.capture());
    ApplicationGroupRoute savedRoute = savedRouteCaptor.getValue();
    assertThat(savedRoute.getApplicationId()).isEqualTo(applicationId);
    assertThat(savedRoute.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.STANDALONE);
    assertThat(savedRoute.getGroupId()).isNull();
    assertThat(savedRoute.getCreatedAt()).isEqualTo(ROUTED_AT);
    assertThat(savedRoute.getUpdatedAt()).isEqualTo(ROUTED_AT);
  }

  @Test
  void ignoresDuplicateApplicationCreationWhenARouteAlreadyExists() {
    UUID applicationId = UUID.randomUUID();
    UUID existingGroupId = UUID.randomUUID();
    ApplicationGroupRoute existingRoute =
        route(applicationId, ApplicationGroupRouteKind.LINKED_GROUP, existingGroupId);
    when(routes.findById(applicationId)).thenReturn(Optional.of(existingRoute));

    projection.on(applicationCreatedEvent(applicationId, ROUTED_AT));

    verify(routes).findById(applicationId);
    verify(routes, never()).save(any());
    verifyNoMoreInteractions(routes);
    assertThat(existingRoute.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(existingRoute.getGroupId()).isEqualTo(existingGroupId);
    assertThat(existingRoute.getUpdatedAt()).isEqualTo(ORIGINAL_OCCURRED_AT);
  }

  @Test
  void transitionsEveryGroupMemberToTheCreatedGroup() {
    UUID firstApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    UUID secondApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID thirdApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID groupId = UUID.randomUUID();
    ApplicationGroupRoute firstRoute =
        route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null);
    ApplicationGroupRoute secondRoute =
        route(secondApplicationId, ApplicationGroupRouteKind.STANDALONE, null);
    ApplicationGroupRoute thirdRoute =
        route(thirdApplicationId, ApplicationGroupRouteKind.STANDALONE, null);
    when(routes.findAllByApplicationIdInForUpdate(
            List.of(secondApplicationId, thirdApplicationId, firstApplicationId)))
        .thenReturn(List.of(secondRoute, thirdRoute, firstRoute));

    projection.on(
        groupCreatedEvent(
            groupId,
            List.of(
                firstApplicationId, secondApplicationId, firstApplicationId, thirdApplicationId),
            ROUTED_AT));

    verify(routes)
        .findAllByApplicationIdInForUpdate(
            List.of(secondApplicationId, thirdApplicationId, firstApplicationId));
    verify(routes).saveAll(List.of(secondRoute, thirdRoute, firstRoute));
    assertThat(List.of(firstRoute, secondRoute, thirdRoute))
        .allSatisfy(
            route -> {
              assertThat(route.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
              assertThat(route.getGroupId()).isEqualTo(groupId);
              assertThat(route.getUpdatedAt()).isEqualTo(ROUTED_AT);
            });
  }

  @Test
  void rejectsGroupCreationWhenAnyMemberRouteIsMissing() {
    UUID firstApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID groupId = UUID.randomUUID();
    ApplicationGroupRoute firstRoute =
        route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null);
    when(routes.findAllByApplicationIdInForUpdate(List.of(firstApplicationId, secondApplicationId)))
        .thenReturn(List.of(firstRoute));

    assertThatThrownBy(
            () ->
                projection.on(
                    groupCreatedEvent(
                        groupId, List.of(secondApplicationId, firstApplicationId), ROUTED_AT)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage(
            "No application group routes found for applications ["
                + firstApplicationId
                + ", "
                + secondApplicationId
                + "]");

    verify(routes, never()).saveAll(any());
  }

  @Test
  void replayingAnEquivalentGroupCreationIsIdempotent() {
    UUID firstApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID groupId = UUID.randomUUID();
    ApplicationGroupRoute firstRoute =
        route(firstApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, groupId);
    ApplicationGroupRoute secondRoute =
        route(secondApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, groupId);
    when(routes.findAllByApplicationIdInForUpdate(List.of(firstApplicationId, secondApplicationId)))
        .thenReturn(List.of(firstRoute, secondRoute));

    projection.on(
        groupCreatedEvent(groupId, List.of(secondApplicationId, firstApplicationId), ROUTED_AT));

    verify(routes).saveAll(List.of(firstRoute, secondRoute));
    assertThat(firstRoute.getUpdatedAt()).isEqualTo(ORIGINAL_OCCURRED_AT);
    assertThat(secondRoute.getUpdatedAt()).isEqualTo(ORIGINAL_OCCURRED_AT);
  }

  @Test
  void transitionsOnlyTheAddedMemberToTheExistingGroup() {
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    ApplicationGroupRoute memberRoute = route(memberId, ApplicationGroupRouteKind.STANDALONE, null);
    when(routes.findByApplicationIdForUpdate(memberId)).thenReturn(Optional.of(memberRoute));

    projection.on(memberAddedEvent(groupId, memberId, ROUTED_AT));

    verify(routes).save(memberRoute);
    assertThat(memberRoute.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(memberRoute.getGroupId()).isEqualTo(groupId);
    assertThat(memberRoute.getUpdatedAt()).isEqualTo(ROUTED_AT);
  }

  @Test
  void rejectsAddedMembersWithoutAnExistingRoute() {
    UUID memberId = UUID.randomUUID();
    when(routes.findByApplicationIdForUpdate(memberId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> projection.on(memberAddedEvent(UUID.randomUUID(), memberId, ROUTED_AT)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No application group route found for application " + memberId);

    verify(routes, never()).save(any());
  }

  @Test
  void replayingAnEquivalentMemberAdditionIsIdempotent() {
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    ApplicationGroupRoute memberRoute =
        route(memberId, ApplicationGroupRouteKind.LINKED_GROUP, groupId);
    when(routes.findByApplicationIdForUpdate(memberId)).thenReturn(Optional.of(memberRoute));

    projection.on(memberAddedEvent(groupId, memberId, ROUTED_AT));

    verify(routes).save(memberRoute);
    assertThat(memberRoute.getUpdatedAt()).isEqualTo(ORIGINAL_OCCURRED_AT);
  }

  @Test
  void rejectsRoutingAnApplicationThatAlreadyBelongsToADifferentGroup() {
    UUID memberId = UUID.randomUUID();
    UUID existingGroupId = UUID.randomUUID();
    UUID newGroupId = UUID.randomUUID();
    ApplicationGroupRoute memberRoute =
        route(memberId, ApplicationGroupRouteKind.LINKED_GROUP, existingGroupId);
    when(routes.findByApplicationIdForUpdate(memberId)).thenReturn(Optional.of(memberRoute));

    assertThatThrownBy(() -> projection.on(memberAddedEvent(newGroupId, memberId, ROUTED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage("Application " + memberId + " already belongs to a different linked group");

    verify(routes, never()).save(any());
  }

  private static ApplicationCreatedEvent applicationCreatedEvent(
      UUID applicationId, Instant occurredAt) {
    return new ApplicationCreatedEvent(
        applicationId, 0L, "fingerprint", "APPLICATION_SUBMITTED", 1, occurredAt, null, List.of());
  }

  private static LinkedApplicationGroupCreatedEvent groupCreatedEvent(
      UUID groupId, List<UUID> memberApplicationIds, Instant occurredAt) {
    return new LinkedApplicationGroupCreatedEvent(
        groupId, memberApplicationIds.getFirst(), memberApplicationIds, occurredAt);
  }

  private static MemberAddedToGroupEvent memberAddedEvent(
      UUID groupId, UUID memberId, Instant occurredAt) {
    return new MemberAddedToGroupEvent(groupId, UUID.randomUUID(), memberId, occurredAt);
  }

  private static ApplicationGroupRoute route(
      UUID applicationId, ApplicationGroupRouteKind routeKind, UUID groupId) {
    return new ApplicationGroupRoute(applicationId, routeKind, groupId, ORIGINAL_OCCURRED_AT);
  }
}
