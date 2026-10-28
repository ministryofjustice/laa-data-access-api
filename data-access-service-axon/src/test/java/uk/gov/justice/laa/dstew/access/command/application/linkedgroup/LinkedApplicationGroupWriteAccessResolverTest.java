package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRoute;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteMembership;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupWriteAccessResolverTest {
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-16T12:00:00Z");

  @Mock private ApplicationGroupRouteRepository routes;

  @Test
  void
      givenTwoStandaloneApplications_whenResolvingLinkApplicationCommand_thenReturnsBothAsTargets() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    var resolver = new LinkedApplicationGroupWriteAccessResolver(routes);
    when(routes.findLinkRoutesForUpdate(
            List.of(sourceId, targetId).stream().sorted().toList(), targetId))
        .thenReturn(List.of(route(sourceId, "0Z123A"), route(targetId, "0Z123A")));

    var targets =
        resolver.resolve(
            new LinkApplicationCommand(
                sourceId, targetId, LinkType.FAMILY, null, OCCURRED_AT, null),
            null);

    assertThat(targets)
        .containsExactly(
            new uk.gov.justice.laa.dstew.access.security.ProtectedResource("0Z123A"),
            new uk.gov.justice.laa.dstew.access.security.ProtectedResource("0Z123A"));
  }

  @Test
  void
      givenTargetApplicationInLinkedGroup_whenResolvingLinkApplicationCommand_thenReturnsSourceAndAllGroupMembers() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    var resolver = new LinkedApplicationGroupWriteAccessResolver(routes);
    when(routes.findLinkRoutesForUpdate(
            List.of(sourceId, targetId).stream().sorted().toList(), targetId))
        .thenReturn(
            List.of(
                route(sourceId, "0Z123A"),
                new ApplicationGroupRoute(
                    targetId,
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "0Z123A",
                    OCCURRED_AT),
                new ApplicationGroupRoute(
                    existingMemberId,
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "0Z123A",
                    OCCURRED_AT)));

    assertThat(
            resolver.resolve(
                new LinkApplicationCommand(
                    sourceId, targetId, LinkType.FAMILY, null, OCCURRED_AT, null),
                null))
        .containsExactly(
            new uk.gov.justice.laa.dstew.access.security.ProtectedResource("0Z123A"),
            new uk.gov.justice.laa.dstew.access.security.ProtectedResource("0Z123A"),
            new uk.gov.justice.laa.dstew.access.security.ProtectedResource("0Z123A"));
  }

  @Test
  void
      givenMissingTargetApplicationRoute_whenResolvingLinkApplicationCommand_thenThrowsResourceNotFoundException() {
    UUID sourceId = UUID.randomUUID();
    UUID missingTargetId = UUID.randomUUID();
    when(routes.findLinkRoutesForUpdate(
            List.of(sourceId, missingTargetId).stream().sorted().toList(), missingTargetId))
        .thenReturn(List.of(route(sourceId, "0Z123A")));

    assertThatThrownBy(
            () ->
                new LinkedApplicationGroupWriteAccessResolver(routes)
                    .resolve(
                        new LinkApplicationCommand(
                            sourceId, missingTargetId, LinkType.FAMILY, null, OCCURRED_AT, null),
                        null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("target application " + missingTargetId);
  }

  @Test
  void
      givenMissingSourceApplicationRoute_whenResolvingLinkApplicationCommand_thenThrowsResourceNotFoundException() {
    UUID missingSourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    when(routes.findLinkRoutesForUpdate(
            List.of(missingSourceId, targetId).stream().sorted().toList(), targetId))
        .thenReturn(List.of(route(targetId, "0Z123A")));

    assertThatThrownBy(
            () ->
                new LinkedApplicationGroupWriteAccessResolver(routes)
                    .resolve(
                        new LinkApplicationCommand(
                            missingSourceId, targetId, LinkType.FAMILY, null, OCCURRED_AT, null),
                        null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("source application " + missingSourceId);
  }

  @Test
  void givenUnlinkApplicationCommand_whenResolving_thenReturnsAddressedApplicationAsTarget() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    var membership = mock(ApplicationGroupRouteMembership.class);
    when(membership.getGroupId()).thenReturn(groupId);
    when(routes.findMembershipByApplicationId(applicationId))
        .thenReturn(java.util.Optional.of(membership));
    when(routes.findAllByGroupIdForUpdate(groupId))
        .thenReturn(
            List.of(
                new ApplicationGroupRoute(
                    applicationId,
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "0Z123A",
                    OCCURRED_AT),
                new ApplicationGroupRoute(
                    UUID.randomUUID(),
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "0Z123A",
                    OCCURRED_AT)));

    assertThat(
            new LinkedApplicationGroupWriteAccessResolver(routes)
                .resolve(
                    new UnlinkApplicationCommand(
                        applicationId,
                        new ExpectedLinkedGroup(UUID.randomUUID(), 1L),
                        OCCURRED_AT,
                        null),
                    null))
        .containsExactly(new uk.gov.justice.laa.dstew.access.security.ProtectedResource("0Z123A"));
  }

  @Test
  void givenMakeApplicationLeadCommand_whenResolving_thenReturnsAddressedApplicationAsTarget() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    var membership = mock(ApplicationGroupRouteMembership.class);
    when(membership.getGroupId()).thenReturn(groupId);
    when(routes.findMembershipByApplicationId(applicationId))
        .thenReturn(java.util.Optional.of(membership));
    when(routes.findAllByGroupIdForUpdate(groupId))
        .thenReturn(
            List.of(
                new ApplicationGroupRoute(
                    applicationId,
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "0Z123A",
                    OCCURRED_AT)));

    assertThat(
            new LinkedApplicationGroupWriteAccessResolver(routes)
                .resolve(
                    new MakeApplicationLeadCommand(
                        applicationId, new ExpectedLinkedGroup(groupId, 1L), OCCURRED_AT, null),
                    null))
        .containsExactly(new uk.gov.justice.laa.dstew.access.security.ProtectedResource("0Z123A"));
  }

  @Test
  void
      givenUnlinkApplicationCommandWithMatchingGroup_whenResolving_thenReturnsAddressedApplicationAsTarget() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    var membership = mock(ApplicationGroupRouteMembership.class);
    when(membership.getGroupId()).thenReturn(groupId);
    when(routes.findMembershipByApplicationId(applicationId))
        .thenReturn(java.util.Optional.of(membership));
    when(routes.findAllByGroupIdForUpdate(groupId))
        .thenReturn(
            List.of(
                new ApplicationGroupRoute(
                    applicationId,
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "0Z123A",
                    OCCURRED_AT),
                new ApplicationGroupRoute(
                    UUID.randomUUID(),
                    ApplicationGroupRouteKind.LINKED_GROUP,
                    groupId,
                    "0Z123A",
                    OCCURRED_AT)));

    assertThat(
            new LinkedApplicationGroupWriteAccessResolver(routes)
                .resolve(
                    new UnlinkApplicationCommand(
                        applicationId, new ExpectedLinkedGroup(groupId, 1L), OCCURRED_AT, null),
                    null))
        .containsExactly(new uk.gov.justice.laa.dstew.access.security.ProtectedResource("0Z123A"));
  }

  @Test
  void
      givenMissingApplicationMembershipRoute_whenResolvingUnlinkApplicationCommand_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    when(routes.findMembershipByApplicationId(applicationId))
        .thenReturn(java.util.Optional.empty());

    assertThatThrownBy(
            () ->
                new LinkedApplicationGroupWriteAccessResolver(routes)
                    .resolve(
                        new UnlinkApplicationCommand(
                            applicationId,
                            new ExpectedLinkedGroup(UUID.randomUUID(), 1L),
                            OCCURRED_AT,
                            null),
                        null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("application " + applicationId);
  }

  @Test
  void
      givenApplicationWithoutLinkedGroupMembership_whenResolvingMakeApplicationLeadCommand_thenThrowsApplicationLinkConflictException() {
    UUID applicationId = UUID.randomUUID();
    var membership = mock(ApplicationGroupRouteMembership.class);
    when(routes.findMembershipByApplicationId(applicationId))
        .thenReturn(java.util.Optional.of(membership));

    assertThatThrownBy(
            () ->
                new LinkedApplicationGroupWriteAccessResolver(routes)
                    .resolve(
                        new MakeApplicationLeadCommand(
                            applicationId,
                            new ExpectedLinkedGroup(UUID.randomUUID(), 1L),
                            OCCURRED_AT,
                            null),
                        null))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("not in a linked group");
  }

  @Test
  void
      givenApplicationAbsentFromLockedGroup_whenResolvingMakeApplicationLeadCommand_thenThrowsApplicationLinkConflictException() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    var membership = mock(ApplicationGroupRouteMembership.class);
    when(membership.getGroupId()).thenReturn(groupId);
    when(routes.findMembershipByApplicationId(applicationId))
        .thenReturn(java.util.Optional.of(membership));
    when(routes.findAllByGroupIdForUpdate(groupId)).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                new LinkedApplicationGroupWriteAccessResolver(routes)
                    .resolve(
                        new MakeApplicationLeadCommand(
                            applicationId, new ExpectedLinkedGroup(groupId, 1L), OCCURRED_AT, null),
                        null))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("not in a linked group");
  }

  private ApplicationGroupRoute route(UUID applicationId, String officeCode) {
    return new ApplicationGroupRoute(
        applicationId, ApplicationGroupRouteKind.STANDALONE, null, officeCode, OCCURRED_AT);
  }
}
