package uk.gov.justice.laa.dstew.access.command.worklist.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRoute;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

class WorkItemRouteProjectionTest {
  private final WorkItemRouteRepository routes = mock(WorkItemRouteRepository.class);
  private final ApplicationGroupRouteRepository applicationGroupRoutes =
      mock(ApplicationGroupRouteRepository.class);
  private final WorkItemRouteProjection projection =
      new WorkItemRouteProjection(routes, applicationGroupRoutes);

  @Test
  void createsStandaloneApplicationRouteWhenManualAssessmentIsRequired() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");
    when(applicationGroupRoutes.findByApplicationIdForUpdate(applicationId))
        .thenReturn(
            Optional.of(
                new ApplicationGroupRoute(
                    applicationId,
                    ApplicationGroupRouteKind.STANDALONE,
                    null,
                    "1A001B",
                    occurredAt)));

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, 0L, occurredAt));

    WorkItemRoute route = savedRoute();
    assertThat(route.getWorkItemType()).isEqualTo(WorkItemType.APPLICATION);
    assertThat(route.getWorkItemId()).isEqualTo(applicationId);
    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    assertThat(route.getMembershipVersion()).isZero();
    assertThat(route.getCreatedAt()).isEqualTo(occurredAt);
    assertThat(route.getUpdatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenGroupedApplication_whenReady_thenLinkedGroupRoute() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");
    when(applicationGroupRoutes.findByApplicationIdForUpdate(applicationId))
        .thenReturn(
            Optional.of(
                new ApplicationGroupRoute(
                    applicationId,
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "1A001B",
                    occurredAt)));

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, 0L, occurredAt));

    WorkItemRoute route = savedRoute();
    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.LINKED_GROUP);
    assertThat(route.getGroupId()).isEqualTo(groupId);
  }

  @Test
  void givenNoApplicationGroupRoute_whenReady_thenStandaloneRoute() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, 0L, occurredAt));

    WorkItemRoute route = savedRoute();
    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
  }

  @Test
  void givenExistingMovedRoute_whenReadyAgain_thenRouteIsPreserved() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant originalTime = Instant.parse("2026-09-03T10:00:00Z");
    Instant occurredAt = originalTime.plusSeconds(60);
    WorkItemRoute route =
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            applicationId,
            WorkItemRouteKind.LINKED_GROUP,
            groupId,
            0L,
            originalTime);
    when(applicationGroupRoutes.findByApplicationIdForUpdate(applicationId))
        .thenReturn(
            Optional.of(
                new ApplicationGroupRoute(
                    applicationId,
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "1A001B",
                    occurredAt)));
    when(routes.findByWorkItemIdForUpdate(applicationId)).thenReturn(Optional.of(route));

    projection.on(
        new ApplicationReadyForManualAssessmentEvent(applicationId, 2L, 2L, 1L, occurredAt));

    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.LINKED_GROUP);
    assertThat(route.getGroupId()).isEqualTo(groupId);
    assertThat(route.getCreatedAt()).isEqualTo(originalTime);
    assertThat(route.getUpdatedAt()).isEqualTo(originalTime);
    verify(routes).findByWorkItemIdForUpdate(applicationId);
    verifyNoMoreInteractions(routes);
  }

  @Test
  void givenRoutedMembers_whenGroupCreated_thenRoutesJoinGroup() {
    UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");
    WorkItemRoute firstRoute =
        new WorkItemRoute(
            WorkItemType.APPLICATION, firstId, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
    WorkItemRoute secondRoute =
        new WorkItemRoute(
            WorkItemType.APPLICATION, secondId, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
    when(routes.findAllByWorkItemIdInForUpdate(List.of(firstId, secondId)))
        .thenReturn(List.of(firstRoute, secondRoute));

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            groupId, firstId, List.of(secondId, firstId), occurredAt));

    assertThat(firstRoute.getGroupId()).isEqualTo(groupId);
    assertThat(secondRoute.getGroupId()).isEqualTo(groupId);
    verify(routes).saveAll(List.of(firstRoute, secondRoute));
  }

  @Test
  void givenRoutedMember_whenMemberAdded_thenRouteJoinsGroup() {
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");
    WorkItemRoute route =
        new WorkItemRoute(
            WorkItemType.APPLICATION, memberId, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
    when(routes.findByWorkItemIdForUpdate(memberId)).thenReturn(Optional.of(route));

    projection.on(new MemberAddedToGroupEvent(groupId, UUID.randomUUID(), memberId, occurredAt));

    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.LINKED_GROUP);
    assertThat(route.getGroupId()).isEqualTo(groupId);
    verify(routes).save(route);
  }

  @Test
  void givenUnroutedMember_whenMemberAdded_thenNothingSaved() {
    UUID memberId = UUID.randomUUID();

    projection.on(
        new MemberAddedToGroupEvent(UUID.randomUUID(), UUID.randomUUID(), memberId, Instant.now()));

    verify(routes).findByWorkItemIdForUpdate(memberId);
    verify(routes, never()).save(any());
  }

  @Test
  void givenRoutedMember_whenMemberRemoved_thenRouteStandalone() {
    UUID memberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");
    WorkItemRoute route =
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            memberId,
            WorkItemRouteKind.LINKED_GROUP,
            groupId,
            0L,
            occurredAt);
    when(routes.findByWorkItemIdForUpdate(memberId)).thenReturn(Optional.of(route));

    projection.on(
        new MemberRemovedFromGroupEvent(
            groupId, UUID.randomUUID(), memberId, 1L, null, 0L, occurredAt));

    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    verify(routes).save(route);
  }

  @Test
  void givenRoutedMembers_whenGroupDissolved_thenRoutesStandalone() {
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");
    WorkItemRoute firstRoute =
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            firstId,
            WorkItemRouteKind.LINKED_GROUP,
            groupId,
            0L,
            occurredAt);
    WorkItemRoute secondRoute =
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            secondId,
            WorkItemRouteKind.LINKED_GROUP,
            groupId,
            0L,
            occurredAt);
    var memberIds = List.of(firstId, secondId);
    var sortedMemberIds = memberIds.stream().sorted().toList();
    when(routes.findAllByWorkItemIdInForUpdate(sortedMemberIds))
        .thenReturn(List.of(firstRoute, secondRoute));

    projection.on(
        new LinkedApplicationGroupDissolvedEvent(
            groupId, firstId, secondId, memberIds, 1L, null, 0L, occurredAt));

    assertThat(firstRoute.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(secondRoute.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    verify(routes).saveAll(List.of(firstRoute, secondRoute));
  }

  @Test
  void deletesApplicationRouteIdempotentlyWhenDecided() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(applicationId, 2L, 2L, "REFUSED", null, Instant.now());

    projection.on(event);
    projection.on(event);

    verify(routes, times(2)).deleteById(applicationId);
  }

  @Test
  void createsStandalonePriorAuthorityRoute() {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");

    projection.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, UUID.randomUUID(), "type", 1, 1L, 0L, occurredAt));

    WorkItemRoute route = savedRoute();
    assertThat(route.getWorkItemType()).isEqualTo(WorkItemType.PRIOR_AUTHORITY);
    assertThat(route.getWorkItemId()).isEqualTo(priorAuthorityId);
    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    assertThat(route.getMembershipVersion()).isZero();
    assertThat(route.getCreatedAt()).isEqualTo(occurredAt);
    assertThat(route.getUpdatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void deletesPriorAuthorityRouteIdempotentlyWhenDecided() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDecisionMadeEvent event =
        new PriorAuthorityDecisionMadeEvent(
            priorAuthorityId,
            UUID.randomUUID(),
            "DISBURSEMENT",
            1L,
            "GRANTED",
            "Recorded",
            BigDecimal.valueOf(100.0),
            Instant.parse("2026-09-03T10:00:00Z"),
            Instant.parse("2026-09-03T10:00:00Z"));

    projection.on(event);
    projection.on(event);

    verify(routes, times(2)).deleteById(priorAuthorityId);
  }

  private WorkItemRoute savedRoute() {
    ArgumentCaptor<WorkItemRoute> routeCaptor = ArgumentCaptor.forClass(WorkItemRoute.class);
    verify(routes).save(routeCaptor.capture());
    return routeCaptor.getValue();
  }
}
