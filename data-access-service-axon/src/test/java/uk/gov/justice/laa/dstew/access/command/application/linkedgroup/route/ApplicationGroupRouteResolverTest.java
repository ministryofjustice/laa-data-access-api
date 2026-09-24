package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Unit tests for write-side application-link routing decisions. */
@ExtendWith(MockitoExtension.class)
class ApplicationGroupRouteResolverTest {
  private static final Instant CREATED_AT = Instant.parse("2026-09-14T09:00:00Z");
  private static final String OFFICE_CODE = "1A001B";

  @Mock private ApplicationGroupRouteRepository routes;

  @InjectMocks private ApplicationGroupRouteResolver resolver;

  @ParameterizedTest(name = "{0}")
  @MethodSource("resolutionScenarios")
  void resolvesExistingRoutes(
      String scenario,
      ApplicationGroupRoute sourceRoute,
      ApplicationGroupRoute targetRoute,
      ApplicationLinkPlan expectedPlan) {
    when(routes.findAllByApplicationIdInForUpdate(sortedIds(sourceRoute, targetRoute)))
        .thenReturn(sortedRoutes(sourceRoute, targetRoute));

    assertThat(resolver.resolve(sourceRoute.getApplicationId(), targetRoute.getApplicationId()))
        .as(scenario)
        .isEqualTo(expectedPlan);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("conflictScenarios")
  void rejectsWhenSourceAlreadyBelongsToADifferentGroup(
      String scenario, ApplicationGroupRoute sourceRoute, ApplicationGroupRoute targetRoute) {
    when(routes.findAllByApplicationIdInForUpdate(sortedIds(sourceRoute, targetRoute)))
        .thenReturn(sortedRoutes(sourceRoute, targetRoute));

    assertThatThrownBy(
            () -> resolver.resolve(sourceRoute.getApplicationId(), targetRoute.getApplicationId()))
        .as(scenario)
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage(
            "Application "
                + sourceRoute.getApplicationId()
                + " already belongs to a different linked group");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidOfficeCodeScenarios")
  void rejectsDifferentOrMissingOfficeCodes(
      String scenario, ApplicationGroupRoute sourceRoute, ApplicationGroupRoute targetRoute) {
    when(routes.findAllByApplicationIdInForUpdate(sortedIds(sourceRoute, targetRoute)))
        .thenReturn(sortedRoutes(sourceRoute, targetRoute));

    assertThatThrownBy(
            () -> resolver.resolve(sourceRoute.getApplicationId(), targetRoute.getApplicationId()))
        .as(scenario)
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly("Applications must have the same office code"));
  }

  @Test
  void rejectsMissingSourceRoute() {
    UUID sourceApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID targetApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000005");
    ApplicationGroupRoute targetRoute =
        route(targetApplicationId, ApplicationGroupRouteKind.STANDALONE, null);
    when(routes.findAllByApplicationIdInForUpdate(
            List.of(sourceApplicationId, targetApplicationId)))
        .thenReturn(List.of(targetRoute));

    assertThatThrownBy(() -> resolver.resolve(sourceApplicationId, targetApplicationId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage(
            "No application group route found for source application " + sourceApplicationId);
  }

  @Test
  void rejectsMissingTargetRoute() {
    UUID sourceApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID targetApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000005");
    ApplicationGroupRoute sourceRoute =
        route(sourceApplicationId, ApplicationGroupRouteKind.STANDALONE, null);
    when(routes.findAllByApplicationIdInForUpdate(
            List.of(sourceApplicationId, targetApplicationId)))
        .thenReturn(List.of(sourceRoute));

    assertThatThrownBy(() -> resolver.resolve(sourceApplicationId, targetApplicationId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage(
            "No application group route found for target application " + targetApplicationId);
  }

  @Test
  void locksRoutesInAscendingIdentifierOrder() {
    UUID laterApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000009");
    UUID earlierApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    ApplicationGroupRoute sourceRoute =
        route(laterApplicationId, ApplicationGroupRouteKind.STANDALONE, null);
    ApplicationGroupRoute targetRoute =
        route(earlierApplicationId, ApplicationGroupRouteKind.STANDALONE, null);
    when(routes.findAllByApplicationIdInForUpdate(
            List.of(earlierApplicationId, laterApplicationId)))
        .thenReturn(List.of(targetRoute, sourceRoute));

    resolver.resolve(laterApplicationId, earlierApplicationId);

    verify(routes)
        .findAllByApplicationIdInForUpdate(List.of(earlierApplicationId, laterApplicationId));
  }

  @Test
  void joinsAStandaloneRouteToANewGroup() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-14T11:00:00Z");
    ApplicationGroupRoute route = route(applicationId, ApplicationGroupRouteKind.STANDALONE, null);

    route.join(groupId, occurredAt);

    assertThat(route.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(route.getGroupId()).isEqualTo(groupId);
    assertThat(route.getUpdatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void joiningTheSameGroupIsIdempotent() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    ApplicationGroupRoute route =
        route(applicationId, ApplicationGroupRouteKind.LINKED_GROUP, groupId);

    route.join(groupId, Instant.parse("2026-09-14T12:00:00Z"));

    assertThat(route.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(route.getGroupId()).isEqualTo(groupId);
    assertThat(route.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void rejectsJoiningADifferentGroup() {
    UUID applicationId = UUID.randomUUID();
    UUID existingGroupId = UUID.randomUUID();
    UUID newGroupId = UUID.randomUUID();
    ApplicationGroupRoute route =
        route(applicationId, ApplicationGroupRouteKind.LINKED_GROUP, existingGroupId);

    assertThatThrownBy(() -> route.join(newGroupId, Instant.parse("2026-09-14T12:00:00Z")))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage(
            "Application " + applicationId + " already belongs to a different linked group");
    assertThat(route.getGroupId()).isEqualTo(existingGroupId);
    assertThat(route.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPlans")
  void rejectsInvalidActionAndGroupCombinations(
      String scenario, ApplicationLinkAction action, UUID groupId, String expectedMessage) {
    assertThatThrownBy(() -> new ApplicationLinkPlan(action, groupId))
        .as(scenario)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(expectedMessage);
  }

  private static Stream<Arguments> resolutionScenarios() {
    UUID firstApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID existingGroupId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    return Stream.of(
        Arguments.of(
            "two standalone routes produce a create-group plan",
            route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null),
            route(secondApplicationId, ApplicationGroupRouteKind.STANDALONE, null),
            new ApplicationLinkPlan(ApplicationLinkAction.CREATE_GROUP, null)),
        Arguments.of(
            "a standalone source and grouped target reuse the target group",
            route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null),
            route(secondApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, existingGroupId),
            new ApplicationLinkPlan(ApplicationLinkAction.ADD_TO_EXISTING_GROUP, existingGroupId)),
        Arguments.of(
            "two routes in the same group are already linked",
            route(firstApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, existingGroupId),
            route(secondApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, existingGroupId),
            new ApplicationLinkPlan(ApplicationLinkAction.ALREADY_LINKED, existingGroupId)));
  }

  private static Stream<Arguments> conflictScenarios() {
    UUID firstApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID sourceGroupId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID targetGroupId = UUID.fromString("20000000-0000-0000-0000-000000000002");
    return Stream.of(
        Arguments.of(
            "a grouped source cannot link to a standalone target",
            route(firstApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, sourceGroupId),
            route(secondApplicationId, ApplicationGroupRouteKind.STANDALONE, null)),
        Arguments.of(
            "a source in one group cannot link to a target in another group",
            route(firstApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, sourceGroupId),
            route(secondApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, targetGroupId)));
  }

  private static Stream<Arguments> invalidOfficeCodeScenarios() {
    UUID firstApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID groupId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    return Stream.of(
        Arguments.of(
            "different office codes",
            route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null, "1A001B"),
            route(secondApplicationId, ApplicationGroupRouteKind.STANDALONE, null, "2B002C")),
        Arguments.of(
            "missing source office code",
            route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null, null),
            route(secondApplicationId, ApplicationGroupRouteKind.STANDALONE, null)),
        Arguments.of(
            "missing target office code",
            route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null),
            route(secondApplicationId, ApplicationGroupRouteKind.STANDALONE, null, null)),
        Arguments.of(
            "both office codes missing",
            route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null, null),
            route(secondApplicationId, ApplicationGroupRouteKind.STANDALONE, null, null)),
        Arguments.of(
            "both office codes blank",
            route(firstApplicationId, ApplicationGroupRouteKind.STANDALONE, null, " "),
            route(secondApplicationId, ApplicationGroupRouteKind.STANDALONE, null, " ")),
        Arguments.of(
            "already linked applications with different office codes",
            route(firstApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, groupId, "1A001B"),
            route(secondApplicationId, ApplicationGroupRouteKind.LINKED_GROUP, groupId, "2B002C")));
  }

  private static Stream<Arguments> invalidPlans() {
    UUID groupId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    return Stream.of(
        Arguments.of(
            "create-group must not carry a group id",
            ApplicationLinkAction.CREATE_GROUP,
            groupId,
            "CREATE_GROUP must not carry a group ID"),
        Arguments.of(
            "existing-group actions must carry a group id",
            ApplicationLinkAction.ADD_TO_EXISTING_GROUP,
            null,
            "ADD_TO_EXISTING_GROUP must carry a group ID"),
        Arguments.of(
            "already-linked actions must carry a group id",
            ApplicationLinkAction.ALREADY_LINKED,
            null,
            "ALREADY_LINKED must carry a group ID"));
  }

  private static List<UUID> sortedIds(
      ApplicationGroupRoute sourceRoute, ApplicationGroupRoute targetRoute) {
    return Stream.of(sourceRoute.getApplicationId(), targetRoute.getApplicationId())
        .sorted()
        .toList();
  }

  private static List<ApplicationGroupRoute> sortedRoutes(
      ApplicationGroupRoute sourceRoute, ApplicationGroupRoute targetRoute) {
    return Stream.of(sourceRoute, targetRoute)
        .sorted((left, right) -> left.getApplicationId().compareTo(right.getApplicationId()))
        .toList();
  }

  private static ApplicationGroupRoute route(
      UUID applicationId, ApplicationGroupRouteKind routeKind, UUID groupId) {
    return route(applicationId, routeKind, groupId, OFFICE_CODE);
  }

  private static ApplicationGroupRoute route(
      UUID applicationId, ApplicationGroupRouteKind routeKind, UUID groupId, String officeCode) {
    return new ApplicationGroupRoute(applicationId, routeKind, groupId, officeCode, CREATED_AT);
  }
}
