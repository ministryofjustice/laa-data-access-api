package uk.gov.justice.laa.dstew.access.command.worklist.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRoute;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class WorkItemRouteResolverTest {
  private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private WorkItemRouteRepository routes;
  @Mock private ApplicationGroupRouteRepository applicationGroupRoutes;
  @InjectMocks private WorkItemRouteResolver resolver;

  @Test
  void givenNoRoute_whenLocked_thenNotFound() {
    UUID workItemId = UUID.randomUUID();

    assertThatThrownBy(() -> resolver.lockRoute(workItemId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No work-item route found for " + workItemId);
  }

  @Test
  void givenNoRoute_whenLockedIfPresent_thenEmptyWithoutLocks() {
    UUID workItemId = UUID.randomUUID();

    assertThat(resolver.lockRouteIfPresent(workItemId)).isEmpty();

    verify(routes, never()).findByWorkItemIdForUpdate(workItemId);
    verify(applicationGroupRoutes, never()).findByApplicationIdForUpdate(workItemId);
  }

  @Test
  void givenPriorAuthorityRoute_whenLocked_thenLocksOnlyItsRoute() {
    UUID workItemId = UUID.randomUUID();
    WorkItemRoute route =
        route(workItemId, WorkItemType.PRIOR_AUTHORITY, WorkItemRouteKind.STANDALONE, null);
    stubMembership(workItemId, WorkItemType.PRIOR_AUTHORITY, WorkItemRouteKind.STANDALONE, null);
    when(routes.findByWorkItemIdForUpdate(workItemId)).thenReturn(Optional.of(route));

    assertThat(resolver.lockRoute(workItemId)).isSameAs(route);

    verify(routes).findByWorkItemIdForUpdate(workItemId);
    verify(applicationGroupRoutes, never()).findByApplicationIdForUpdate(workItemId);
    verify(applicationGroupRoutes, never()).findAllByGroupIdForUpdate(any());
  }

  @Test
  void givenStandaloneApplication_whenLocked_thenLocksInGlobalOrder() {
    UUID workItemId = UUID.randomUUID();
    WorkItemRoute route =
        route(workItemId, WorkItemType.APPLICATION, WorkItemRouteKind.STANDALONE, null);
    stubMembership(workItemId, WorkItemType.APPLICATION, WorkItemRouteKind.STANDALONE, null);
    when(applicationGroupRoutes.findByApplicationIdForUpdate(workItemId))
        .thenReturn(
            Optional.of(
                new ApplicationGroupRoute(
                    workItemId, ApplicationGroupRouteKind.STANDALONE, null, "1A001B", CREATED_AT)));
    when(routes.findByWorkItemIdForUpdate(workItemId)).thenReturn(Optional.of(route));

    assertThat(resolver.lockRoute(workItemId)).isSameAs(route);

    InOrder inOrder = inOrder(applicationGroupRoutes, routes);
    inOrder.verify(applicationGroupRoutes).findByApplicationIdForUpdate(workItemId);
    inOrder.verify(routes).findByWorkItemIdForUpdate(workItemId);
  }

  @Test
  void givenGroupedApplication_whenLocked_thenLocksGroupThenItem() {
    UUID workItemId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    WorkItemRoute route =
        route(workItemId, WorkItemType.APPLICATION, WorkItemRouteKind.LINKED_GROUP, groupId);
    stubMembership(workItemId, WorkItemType.APPLICATION, WorkItemRouteKind.LINKED_GROUP, groupId);
    when(applicationGroupRoutes.findAllByGroupIdForUpdate(groupId))
        .thenReturn(
            List.of(
                new ApplicationGroupRoute(
                    workItemId,
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "1A001B",
                    CREATED_AT)));
    when(routes.findByWorkItemIdForUpdate(workItemId)).thenReturn(Optional.of(route));

    assertThat(resolver.lockRoute(workItemId)).isSameAs(route);

    InOrder inOrder = inOrder(applicationGroupRoutes, routes);
    inOrder.verify(applicationGroupRoutes).findAllByGroupIdForUpdate(groupId);
    inOrder.verify(routes).findByWorkItemIdForUpdate(workItemId);
  }

  @Test
  void givenRouteChangedBeforeLock_whenLocked_thenConflict() {
    UUID workItemId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    WorkItemRouteMembership membership = mock(WorkItemRouteMembership.class);
    when(membership.getWorkItemType()).thenReturn(WorkItemType.APPLICATION);
    when(membership.getRouteKind()).thenReturn(WorkItemRouteKind.STANDALONE);
    when(routes.findMembershipByWorkItemId(workItemId)).thenReturn(Optional.of(membership));
    when(applicationGroupRoutes.findByApplicationIdForUpdate(workItemId))
        .thenReturn(
            Optional.of(
                new ApplicationGroupRoute(
                    workItemId, ApplicationGroupRouteKind.STANDALONE, null, "1A001B", CREATED_AT)));
    when(routes.findByWorkItemIdForUpdate(workItemId))
        .thenReturn(
            Optional.of(
                route(
                    workItemId,
                    WorkItemType.APPLICATION,
                    WorkItemRouteKind.LINKED_GROUP,
                    groupId)));

    assertThatThrownBy(() -> resolver.lockRoute(workItemId))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage(
            "Work item "
                + workItemId
                + " cannot be updated: its linked group membership changed; re-read before retrying");
  }

  @Test
  void givenGroupedMembershipWithoutGroupId_whenLocked_thenConflict() {
    UUID workItemId = UUID.randomUUID();
    WorkItemRouteMembership membership =
        membership(WorkItemType.APPLICATION, WorkItemRouteKind.LINKED_GROUP, null);
    when(routes.findMembershipByWorkItemId(workItemId)).thenReturn(Optional.of(membership));

    assertThatThrownBy(() -> resolver.lockRoute(workItemId))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage(
            "Work item "
                + workItemId
                + " cannot be updated: its linked group membership changed; re-read before retrying");

    verify(applicationGroupRoutes, never()).findAllByGroupIdForUpdate(any());
    verify(routes, never()).findByWorkItemIdForUpdate(workItemId);
  }

  @Test
  void givenGroupChangedBeforeLock_whenLocked_thenConflict() {
    UUID workItemId = UUID.randomUUID();
    UUID peekGroupId = UUID.randomUUID();
    UUID lockedGroupId = UUID.randomUUID();
    stubMembership(
        workItemId, WorkItemType.APPLICATION, WorkItemRouteKind.LINKED_GROUP, peekGroupId);
    when(applicationGroupRoutes.findAllByGroupIdForUpdate(peekGroupId)).thenReturn(List.of());
    when(routes.findByWorkItemIdForUpdate(workItemId))
        .thenReturn(
            Optional.of(
                route(
                    workItemId,
                    WorkItemType.APPLICATION,
                    WorkItemRouteKind.LINKED_GROUP,
                    lockedGroupId)));

    assertThatThrownBy(() -> resolver.lockRoute(workItemId))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage(
            "Work item "
                + workItemId
                + " cannot be updated: its linked group membership changed; re-read before retrying");
  }

  @Test
  void givenRouteRemovedBeforeLock_whenLockedIfPresent_thenEmpty() {
    UUID workItemId = UUID.randomUUID();
    WorkItemRouteMembership membership = mock(WorkItemRouteMembership.class);
    when(membership.getWorkItemType()).thenReturn(WorkItemType.APPLICATION);
    when(membership.getRouteKind()).thenReturn(WorkItemRouteKind.STANDALONE);
    when(routes.findMembershipByWorkItemId(workItemId)).thenReturn(Optional.of(membership));
    when(applicationGroupRoutes.findByApplicationIdForUpdate(workItemId))
        .thenReturn(
            Optional.of(
                new ApplicationGroupRoute(
                    workItemId, ApplicationGroupRouteKind.STANDALONE, null, "1A001B", CREATED_AT)));
    when(routes.findByWorkItemIdForUpdate(workItemId)).thenReturn(Optional.empty());

    assertThat(resolver.lockRouteIfPresent(workItemId)).isEmpty();
  }

  @Test
  void givenUnsortedApplications_whenRoutesLocked_thenLockedAscending() {
    UUID laterId = UUID.fromString("00000000-0000-0000-0000-000000000009");
    UUID earlierId = UUID.fromString("00000000-0000-0000-0000-000000000003");

    resolver.lockApplicationRoutes(List.of(laterId, earlierId));

    verify(routes).findAllByWorkItemIdInForUpdate(List.of(earlierId, laterId));
  }

  @Test
  void givenGroup_whenGroupRoutesLocked_thenRepositoryLocksGroup() {
    UUID groupId = UUID.randomUUID();

    resolver.lockGroupRoutes(groupId);

    verify(routes).findAllByGroupIdForUpdate(groupId);
  }

  private WorkItemRoute route(UUID id, WorkItemType type, WorkItemRouteKind kind, UUID groupId) {
    return new WorkItemRoute(type, id, kind, groupId, 0L, CREATED_AT);
  }

  private WorkItemRouteMembership membership(
      WorkItemType type, WorkItemRouteKind kind, UUID groupId) {
    WorkItemRouteMembership membership = mock(WorkItemRouteMembership.class);
    when(membership.getWorkItemType()).thenReturn(type);
    when(membership.getRouteKind()).thenReturn(kind);
    when(membership.getGroupId()).thenReturn(groupId);
    return membership;
  }

  private void stubMembership(
      UUID workItemId, WorkItemType type, WorkItemRouteKind kind, UUID groupId) {
    var membership = membership(type, kind, groupId);
    when(routes.findMembershipByWorkItemId(workItemId)).thenReturn(Optional.of(membership));
  }
}
