package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.AddApplicationToLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.RemoveApplicationFromLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRoute;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.version.VersionToken;
import util.ProjectionAwaiter;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ApplicationLinkMembershipIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private CommandGateway commandGateway;

  @Autowired private QueryGateway queryGateway;

  @Autowired private ApplicationGroupRouteRepository routeRepository;

  @Autowired private LinkedApplicationGroupReadRepository groupRepository;

  private ProjectionAwaiter projectionAwaiter;

  @PostConstruct
  void initialiseProjectionAwaiter() {
    projectionAwaiter = new ProjectionAwaiter(queryGateway);
  }

  @Test
  void givenAssociate_whenMadeLead_thenLeadChangesAndRoutesUnchanged() {
    var group = createGroup(3);
    var routesBefore = routeMembership(group.memberIds());
    long leadChangedBefore =
        countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class);
    UUID newLeadId = group.memberIds().get(1);

    assertThat(makeLead(newLeadId, token(group.groupId(), group.version())).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(newLeadId, group.version() + 1);
    awaitMembership(group.memberIds(), newLeadId);

    assertThat(routeMembership(group.memberIds())).isEqualTo(routesBefore);
    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isEqualTo(leadChangedBefore + 1);
    awaitHistory(newLeadId, "APPLICATION_GROUP_LEAD_CHANGED");
    awaitHistory(group.leadId(), "APPLICATION_GROUP_LEAD_CHANGED");
    assertThat(application(newLeadId).getIsLead()).isTrue();
    assertThat(application(group.leadId()).getIsLead()).isFalse();
  }

  @Test
  void givenCurrentLead_whenMadeLead_thenNoEventAppended() {
    var group = createGroup(2);
    long eventCount =
        countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class);

    assertThat(makeLead(group.leadId(), token(group.groupId(), 99)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isEqualTo(eventCount);
  }

  @Test
  void givenStandaloneApplication_whenMadeLead_thenConflict() {
    UUID applicationId = createApplication(uniqueLastName());

    var routeBefore = routeMembership(List.of(applicationId));
    assertThat(makeLead(applicationId, token(UUID.randomUUID(), 0)).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(routeMembership(List.of(applicationId))).isEqualTo(routeBefore);
    assertThat(application(applicationId).getLinkedGroupVersion()).isNull();
    assertThat(historyCount(applicationId, "APPLICATION_GROUP_LEAD_CHANGED")).isZero();
  }

  @Test
  void givenUnknownApplication_whenMadeLead_thenNotFound() {
    assertThat(makeLead(UUID.randomUUID(), token(UUID.randomUUID(), 0)).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenThreeMemberGroup_whenAssociateUnlinked_thenOnlyAssociateLeaves() {
    var group = createGroup(3);
    UUID removedId = group.memberIds().get(1);

    assertThat(unlink(removedId, token(group.groupId(), group.version())).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitStandalone(removedId);
    awaitMembership(List.of(group.leadId(), group.memberIds().get(2)), group.leadId());

    assertThat(route(removedId).getGroupId()).isNull();
    assertThat(route(group.leadId()).getGroupId()).isEqualTo(group.groupId());
    assertThat(route(group.memberIds().get(2)).getGroupId()).isEqualTo(group.groupId());
    awaitGroupMembers(group.groupId(), group.leadId(), group.memberIds().get(2));
    assertThat(group(group.groupId()).getMemberIds())
        .containsExactlyInAnyOrder(group.leadId(), group.memberIds().get(2));
    assertThat(countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)).isOne();
    awaitHistory(removedId, "APPLICATION_GROUP_LEFT");
  }

  @Test
  void givenTwoMemberGroup_whenAssociateUnlinked_thenGroupDissolves() {
    var group = createGroup(2);
    UUID removedId = group.memberIds().get(1);

    assertThat(unlink(removedId, token(group.groupId(), group.version())).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupDeleted(group.groupId());
    awaitStandalone(group.leadId());
    awaitStandalone(removedId);

    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class))
        .isOne();
    assertThat(application(group.leadId()).getLinkedApplications()).isEmpty();
    assertThat(application(removedId).getLinkedApplications()).isEmpty();
    awaitHistory(removedId, "APPLICATION_GROUP_LEFT");
    awaitHistory(group.leadId(), "APPLICATION_GROUP_DISSOLVED");
  }

  @Test
  void givenLead_whenUnlinked_thenConflict() {
    var group = createGroup(2);

    var routesBefore = routeMembership(group.memberIds());
    assertThat(unlink(group.leadId(), token(group.groupId(), group.version())).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    awaitFinalGroupState(
        group.groupId(), group.memberIds(), group.memberIds(), group.leadId(), group.version());
    assertThat(routeMembership(group.memberIds())).isEqualTo(routesBefore);
    assertThat(countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)).isZero();
    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class))
        .isZero();
    assertThat(historyCount(group.leadId(), "APPLICATION_GROUP_LEFT")).isZero();
  }

  @Test
  void givenStandaloneApplication_whenUnlinked_thenConflict() {
    UUID applicationId = createApplication(uniqueLastName());

    var routeBefore = routeMembership(List.of(applicationId));
    assertThat(unlink(applicationId, token(UUID.randomUUID(), 0)).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(routeMembership(List.of(applicationId))).isEqualTo(routeBefore);
    assertThat(application(applicationId).getLinkedGroupVersion()).isNull();
    assertThat(historyCount(applicationId, "APPLICATION_GROUP_LEFT")).isZero();
  }

  @Test
  void givenUnknownApplication_whenUnlinked_thenNotFound() {
    assertThat(unlink(UUID.randomUUID(), token(UUID.randomUUID(), 0)).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenUnlinkedApplication_whenLinkedAgain_thenJoinsNewGroup() {
    var group = createGroup(3);
    UUID removedId = group.memberIds().get(1);
    UUID newTargetId = createApplication(group.lastName());
    assertThat(unlink(removedId, token(group.groupId(), group.version())).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitStandalone(removedId);

    assertThat(link(removedId, newTargetId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    var newRoute = awaitRoute(removedId, ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(newRoute.getGroupId()).isNotEqualTo(group.groupId());
    awaitGroupProjection(newRoute.getGroupId(), List.of(removedId, newTargetId));
    assertThat(group(newRoute.getGroupId()).getMemberIds())
        .containsExactlyInAnyOrder(removedId, newTargetId);
  }

  @Test
  void givenLeadChanged_whenPreviousLeadUnlinked_thenSucceeds() {
    var group = createGroup(3);
    UUID newLeadId = group.memberIds().get(1);

    assertThat(makeLead(newLeadId, token(group.groupId(), group.version())).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(newLeadId, group.version() + 1);

    assertThat(unlink(group.leadId(), token(group.groupId(), group.version() + 1)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitStandalone(group.leadId());
    assertThat(route(newLeadId).getGroupId()).isEqualTo(group.groupId());
  }

  @Test
  void givenStaleVersion_whenMadeLead_thenConflict() {
    var group = createGroup(3);
    UUID additionalMemberId = createApplication(group.lastName());
    assertThat(
            link(additionalMemberId, group.leadId(), token(group.groupId(), group.version()))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(additionalMemberId, group.version() + 1);
    long leadChangedCount =
        countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class);

    var conflict =
        groupChangeWithBody(
            group.memberIds().get(1), token(group.groupId(), group.version()), "make-lead");
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody())
        .contains(
            "Linked group of application "
                + group.memberIds().get(1)
                + " does not match the supplied linkedGroupVersion; re-read before retrying");
    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isEqualTo(leadChangedCount);
    var allMembers = new ArrayList<>(group.memberIds());
    allMembers.add(additionalMemberId);
    awaitFinalGroupState(
        group.groupId(), allMembers, allMembers, group.leadId(), group.version() + 1);
    assertThat(historyCount(group.memberIds().get(1), "APPLICATION_GROUP_LEAD_CHANGED")).isZero();
  }

  @Test
  void givenStaleVersion_whenUnlinked_thenConflict() {
    var group = createGroup(3);
    UUID additionalMemberId = createApplication(group.lastName());
    assertThat(
            link(additionalMemberId, group.leadId(), token(group.groupId(), group.version()))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(additionalMemberId, group.version() + 1);
    long removalEventCount =
        countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)
            + countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class);

    var conflict =
        groupChangeWithBody(
            group.memberIds().get(1), token(group.groupId(), group.version()), "unlink");
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody())
        .contains(
            "Linked group of application "
                + group.memberIds().get(1)
                + " does not match the supplied linkedGroupVersion; re-read before retrying");
    assertThat(
            countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)
                + countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class))
        .isEqualTo(removalEventCount);
    var allMembers = new ArrayList<>(group.memberIds());
    allMembers.add(additionalMemberId);
    awaitFinalGroupState(
        group.groupId(), allMembers, allMembers, group.leadId(), group.version() + 1);
    assertThat(historyCount(group.memberIds().get(1), "APPLICATION_GROUP_LEFT")).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"unlink", "make-lead"})
  void givenStaleTokenFromDissolvedGroup_whenAppliedToNewGroup_thenConflict(String operation) {
    var lastName = uniqueLastName();
    var applicationA = createApplication(lastName);
    var applicationB = createApplication(lastName);
    var applicationC = createApplication(lastName);

    assertThat(link(applicationA, applicationB, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var group1 = awaitRoute(applicationA, ApplicationGroupRouteKind.LINKED_GROUP).getGroupId();
    awaitGroupProjection(group1, List.of(applicationB, applicationA));
    assertThat(group(group1).getLeadApplicationId()).isEqualTo(applicationB);
    var staleToken =
        await()
            .atMost(15, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(
                () -> application(applicationA).getLinkedGroupVersion(),
                version -> version != null);
    assertThat(staleToken).isEqualTo(token(group1, 0));

    assertThat(unlink(applicationA, staleToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    awaitStandalone(applicationA);

    assertThat(link(applicationA, applicationC, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var group2 = awaitRoute(applicationA, ApplicationGroupRouteKind.LINKED_GROUP).getGroupId();
    assertThat(group2).isNotEqualTo(group1);
    awaitGroupProjection(group2, List.of(applicationC, applicationA));
    assertThat(group(group2).getLeadApplicationId()).isEqualTo(applicationC);

    var conflict = groupChangeWithBody(applicationA, staleToken, operation);
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody())
        .contains("is no longer in the linked group identified by linkedGroupVersion");

    awaitFinalGroupState(
        group2,
        List.of(applicationA, applicationC),
        List.of(applicationC, applicationA),
        applicationC,
        0);
    assertThat(historyCount(applicationA, "APPLICATION_GROUP_LEAD_CHANGED")).isZero();
    assertThat(countDomainEvents(group2, MemberRemovedFromGroupEvent.class)).isZero();
    assertThat(countDomainEvents(group2, LinkedApplicationGroupDissolvedEvent.class)).isZero();
    assertThat(countDomainEvents(group2, LinkedApplicationGroupLeadChangedEvent.class)).isZero();
  }

  @Test
  void givenStaleTargetTokenFromDissolvedGroup_whenLinking_thenConflict() {
    var lastName = uniqueLastName();
    var applicationA = createApplication(lastName);
    var applicationB = createApplication(lastName);
    var applicationD = createApplication(lastName);
    var applicationE = createApplication(lastName);

    assertThat(link(applicationA, applicationB, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var group1 = awaitRoute(applicationB, ApplicationGroupRouteKind.LINKED_GROUP).getGroupId();
    awaitGroupProjection(group1, List.of(applicationB, applicationA));
    assertThat(group(group1).getLeadApplicationId()).isEqualTo(applicationB);
    var staleToken =
        await()
            .atMost(15, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(
                () -> application(applicationB).getLinkedGroupVersion(),
                version -> version != null);
    assertThat(staleToken).isEqualTo(token(group1, 0));

    assertThat(unlink(applicationA, staleToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    awaitStandalone(applicationB);
    assertThat(link(applicationB, applicationD, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var group2 = awaitRoute(applicationB, ApplicationGroupRouteKind.LINKED_GROUP).getGroupId();
    assertThat(group2).isNotEqualTo(group1);
    awaitGroupProjection(group2, List.of(applicationD, applicationB));
    assertThat(group(group2).getLeadApplicationId()).isEqualTo(applicationD);

    assertThat(link(applicationE, applicationB, staleToken).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    awaitFinalGroupState(
        group2,
        List.of(applicationB, applicationD, applicationE),
        List.of(applicationD, applicationB),
        applicationD,
        0);
    assertThat(countDomainEvents(group2, MemberAddedToGroupEvent.class)).isZero();
    assertThat(historyCount(applicationE, "APPLICATION_GROUP_JOINED")).isZero();
  }

  @Test
  void givenLinkedApplication_whenFetched_thenExposesLinkedGroupVersion() {
    var group = createGroup(2);
    assertThat(application(group.leadId()).getLinkedGroupVersion())
        .isEqualTo(token(group.groupId(), 0));

    UUID thirdMemberId = createApplication(group.lastName());
    List<UUID> allMemberIds = new ArrayList<>(group.memberIds());
    allMemberIds.add(thirdMemberId);
    assertThat(link(thirdMemberId, group.leadId(), token(group.groupId(), 0)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(thirdMemberId, 1);
    assertThat(application(thirdMemberId).getLinkedGroupVersion())
        .isEqualTo(token(group.groupId(), 1));

    UUID newLeadId = group.memberIds().get(1);
    assertThat(makeLead(newLeadId, token(group.groupId(), 1)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(newLeadId, 2);
    assertThat(application(newLeadId).getLinkedGroupVersion()).isEqualTo(token(group.groupId(), 2));

    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              var listResponse =
                  restTemplate.exchange(
                      "http://localhost:"
                          + port
                          + "/api/v0/applications?clientLastName="
                          + group.lastName()
                          + "&pageSize=100",
                      HttpMethod.GET,
                      new HttpEntity<>(headers()),
                      ApplicationSummaryResponse.class);
              assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
              var summaries =
                  listResponse.getBody().getApplications().stream()
                      .filter(summary -> allMemberIds.contains(summary.getApplicationId()))
                      .toList();
              assertThat(summaries)
                  .extracting(ApplicationSummary::getApplicationId)
                  .containsExactlyInAnyOrderElementsOf(allMemberIds);
              assertThat(summaries)
                  .allSatisfy(
                      summary -> {
                        assertThat(summary.getLinkedGroupVersion())
                            .isEqualTo(token(group.groupId(), 2));
                        assertThat(summary.getIsLead())
                            .isEqualTo(summary.getApplicationId().equals(newLeadId));
                        assertThat(summary.getLinkedApplications())
                            .extracting(LinkedApplicationSummaryResponse::getApplicationId)
                            .containsExactlyInAnyOrderElementsOf(
                                allMemberIds.stream()
                                    .filter(id -> !id.equals(summary.getApplicationId()))
                                    .toList());
                        assertThat(summary.getLinkedApplications())
                            .allSatisfy(
                                linked ->
                                    assertThat(linked.getIsLead())
                                        .isEqualTo(linked.getApplicationId().equals(newLeadId)));
                      });
            });

    assertThat(unlink(thirdMemberId, token(group.groupId(), 2)).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitStandalone(thirdMemberId);
    assertThat(application(thirdMemberId).getLinkedGroupVersion()).isNull();
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              var summaries =
                  restTemplate.exchange(
                      "http://localhost:"
                          + port
                          + "/api/v0/applications?clientLastName="
                          + group.lastName()
                          + "&pageSize=100",
                      HttpMethod.GET,
                      new HttpEntity<>(headers()),
                      ApplicationSummaryResponse.class);
              assertThat(summaries.getBody().getApplications())
                  .filteredOn(summary -> summary.getApplicationId().equals(thirdMemberId))
                  .singleElement()
                  .satisfies(
                      summary -> {
                        assertThat(summary.getLinkedGroupVersion()).isNull();
                        assertThat(summary.getLinkedApplications()).isEmpty();
                      });
            });
  }

  @ParameterizedTest
  @MethodSource("invalidGroupChangeRequests")
  void givenInvalidVersion_whenChangingGroup_thenBadRequest(
      String operation, String body, String expectedError) {
    var applicationId = createApplication(uniqueLastName());
    var rawHeaders = headers();
    rawHeaders.setContentType(MediaType.APPLICATION_JSON);

    var response =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/" + operation,
            HttpMethod.POST,
            new HttpEntity<>(body, rawHeaders),
            String.class);

    assertValidationError(response, expectedError);
    assertThat(route(applicationId).getRouteKind()).isEqualTo(ApplicationGroupRouteKind.STANDALONE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-token", "3", "negative"})
  void givenInvalidVersion_whenJoiningGroup_thenBadRequest(String invalidVersion) {
    var group = createGroup(2);
    var sourceId = createApplication(group.lastName());
    var rawHeaders = headers();
    rawHeaders.setContentType(MediaType.APPLICATION_JSON);
    var versionValue =
        "negative".equals(invalidVersion)
            ? "\"" + negativeToken(group.groupId()) + "\""
            : "3".equals(invalidVersion) ? "3" : "\"not-a-token\"";
    var body =
        "{\"applicationId\":\""
            + group.leadId()
            + "\",\"linkType\":\"FAMILY\",\"linkedGroupVersion\":"
            + versionValue
            + "}";

    var response =
        restTemplate.exchange(
            "/api/v0/applications/" + sourceId + "/link",
            HttpMethod.POST,
            new HttpEntity<>(body, rawHeaders),
            String.class);

    assertValidationError(
        response, "linkedGroupVersion: must be a valid linked group version token");
    assertThat(route(sourceId).getRouteKind()).isEqualTo(ApplicationGroupRouteKind.STANDALONE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "null"})
  void givenNoTargetVersion_whenJoiningGroup_thenConflict(String versionForm) {
    var group = createGroup(2);
    var sourceId = createApplication(group.lastName());
    var rawHeaders = headers();
    rawHeaders.setContentType(MediaType.APPLICATION_JSON);
    var body =
        "{\"applicationId\":\""
            + group.leadId()
            + "\",\"linkType\":\"FAMILY\""
            + ("null".equals(versionForm) ? ",\"linkedGroupVersion\":null" : "")
            + "}";

    var response =
        restTemplate.exchange(
            "/api/v0/applications/" + sourceId + "/link",
            HttpMethod.POST,
            new HttpEntity<>(body, rawHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("linkedGroupVersion is required");
    assertThat(countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class)).isZero();
    awaitFinalGroupState(
        group.groupId(),
        List.of(group.leadId(), group.memberIds().get(1), sourceId),
        group.memberIds(),
        group.leadId(),
        group.version());
  }

  private static List<Arguments> invalidGroupChangeRequests() {
    var negative = "{\"linkedGroupVersion\":\"" + negativeToken(UUID.randomUUID()) + "\"}";
    var bodies =
        List.of(
            Arguments.of("{}", "linkedGroupVersion: must not be null"),
            Arguments.of("{\"linkedGroupVersion\":null}", "linkedGroupVersion: must not be null"),
            Arguments.of(
                "{\"linkedGroupVersion\":\"not-a-token\"}",
                "linkedGroupVersion: must be a valid linked group version token"),
            Arguments.of(
                "{\"linkedGroupVersion\":3}",
                "linkedGroupVersion: must be a valid linked group version token"),
            Arguments.of(
                negative, "linkedGroupVersion: must be a valid linked group version token"));
    return List.of("make-lead", "unlink").stream()
        .flatMap(
            operation ->
                bodies.stream().map(body -> Arguments.of(operation, body.get()[0], body.get()[1])))
        .toList();
  }

  private static String negativeToken(UUID groupId) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(("v1:linked-group:" + groupId + ":-1").getBytes(StandardCharsets.UTF_8));
  }

  private void assertValidationError(ResponseEntity<String> response, String expectedError) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("\"status\":400", "\"detail\":\"Generic Validation Error\"")
        .contains("\"errors\":[\"" + expectedError + "\"]");
  }

  @RepeatedTest(3)
  void givenConcurrentAdds_whenTargetGroupVersionMatches_thenOnlyOneJoins() throws Exception {
    var group = createGroup(2);
    var firstSourceId = createApplication(group.lastName());
    var secondSourceId = createApplication(group.lastName());
    var results =
        race(
            () -> link(firstSourceId, group.leadId(), token(group.groupId(), group.version())),
            () -> link(secondSourceId, group.leadId(), token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    var successfulSourceId =
        results.get(0).getStatusCode() == HttpStatus.NO_CONTENT ? firstSourceId : secondSourceId;
    var rejectedSourceId =
        successfulSourceId.equals(firstSourceId) ? secondSourceId : firstSourceId;
    assertThat(countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class)).isOne();
    awaitFinalGroupState(
        group.groupId(),
        List.of(group.leadId(), group.memberIds().get(1), firstSourceId, secondSourceId),
        List.of(group.leadId(), group.memberIds().get(1), successfulSourceId),
        group.leadId(),
        group.version() + 1);
    assertThat(historyCount(rejectedSourceId, "APPLICATION_GROUP_JOINED")).isZero();
  }

  @RepeatedTest(3)
  void givenConcurrentIdenticalLinks_whenStandalone_thenOneGroupIsCreated() throws Exception {
    var lastName = uniqueLastName();
    var sourceId = createApplication(lastName);
    var targetId = createApplication(lastName);
    var createdBefore = countAllDomainEvents(LinkedApplicationGroupCreatedEvent.class);
    var results = race(() -> link(sourceId, targetId, null), () -> link(sourceId, targetId, null));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsOnly(HttpStatus.NO_CONTENT);
    var groupId = awaitRoute(sourceId, ApplicationGroupRouteKind.LINKED_GROUP).getGroupId();
    assertThat(countDomainEvents(groupId, LinkedApplicationGroupCreatedEvent.class)).isOne();
    assertThat(countAllDomainEvents(LinkedApplicationGroupCreatedEvent.class))
        .isEqualTo(createdBefore + 1);
    awaitFinalGroupState(
        groupId, List.of(sourceId, targetId), List.of(targetId, sourceId), targetId, 0);
  }

  @Test
  void givenConcurrentAddAndUnlink_whenThreeMembers_thenOneConflicts() throws Exception {
    var group = createGroup(3);
    var sourceId = createApplication(group.lastName());
    var removedId = group.memberIds().get(1);
    var previousAddCount = countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class);
    var results =
        race(
            () -> link(sourceId, group.leadId(), token(group.groupId(), group.version())),
            () -> unlink(removedId, token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(
            countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class)
                - previousAddCount
                + countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class))
        .isOne();
    var addSucceeded = results.get(0).getStatusCode() == HttpStatus.NO_CONTENT;
    var expectedMembers = new ArrayList<>(group.memberIds());
    if (addSucceeded) {
      expectedMembers.add(sourceId);
      assertThat(historyCount(removedId, "APPLICATION_GROUP_LEFT")).isZero();
    } else {
      expectedMembers.remove(removedId);
      assertThat(historyCount(sourceId, "APPLICATION_GROUP_JOINED")).isZero();
    }
    var allParticipants = new ArrayList<>(group.memberIds());
    allParticipants.add(sourceId);
    awaitFinalGroupState(
        group.groupId(), allParticipants, expectedMembers, group.leadId(), group.version() + 1);
  }

  @Test
  void givenConcurrentAddAndMakeLead_whenThreeMembers_thenOneConflicts() throws Exception {
    var group = createGroup(3);
    var sourceId = createApplication(group.lastName());
    var newLeadId = group.memberIds().get(1);
    var previousAddCount = countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class);
    var results =
        race(
            () -> link(sourceId, group.leadId(), token(group.groupId(), group.version())),
            () -> makeLead(newLeadId, token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(
            countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class)
                - previousAddCount
                + countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isOne();
    var addSucceeded = results.get(0).getStatusCode() == HttpStatus.NO_CONTENT;
    var allParticipants = new ArrayList<>(group.memberIds());
    allParticipants.add(sourceId);
    var expectedMembers = addSucceeded ? allParticipants : group.memberIds();
    awaitFinalGroupState(
        group.groupId(),
        allParticipants,
        expectedMembers,
        addSucceeded ? group.leadId() : newLeadId,
        group.version() + 1);
  }

  @Test
  void givenConcurrentIdenticalMakeLead_whenRetried_thenOnlyOneEvent() throws Exception {
    var group = createGroup(3);
    var newLeadId = group.memberIds().get(1);
    var results =
        race(
            () -> makeLead(newLeadId, token(group.groupId(), group.version())),
            () -> makeLead(newLeadId, token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsOnly(HttpStatus.NO_CONTENT);
    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isOne();
    awaitFinalGroupState(
        group.groupId(), group.memberIds(), group.memberIds(), newLeadId, group.version() + 1);
  }

  @Test
  void givenConcurrentIdenticalUnlinks_whenRetried_thenOnlyOneEvent() throws Exception {
    var group = createGroup(3);
    var removedId = group.memberIds().get(1);
    var results =
        race(
            () -> unlink(removedId, token(group.groupId(), group.version())),
            () -> unlink(removedId, token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)).isOne();
    awaitFinalGroupState(
        group.groupId(),
        group.memberIds(),
        List.of(group.leadId(), group.memberIds().get(2)),
        group.leadId(),
        group.version() + 1);
  }

  @Test
  void givenConcurrentIdenticalGroupLinks_whenRetried_thenOnlyOneEvent() throws Exception {
    var group = createGroup(2);
    var sourceId = createApplication(group.lastName());
    var results =
        race(
            () -> link(sourceId, group.leadId(), token(group.groupId(), group.version())),
            () -> link(sourceId, group.leadId(), token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsOnly(HttpStatus.NO_CONTENT);
    assertThat(countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class)).isOne();
    var expectedMembers = List.of(group.leadId(), group.memberIds().get(1), sourceId);
    awaitFinalGroupState(
        group.groupId(), expectedMembers, expectedMembers, group.leadId(), group.version() + 1);
  }

  @Test
  void givenConcurrentAddsToDifferentGroups_whenLinked_thenBothSucceed() throws Exception {
    var firstGroup = createGroup(2);
    var secondGroup = createGroup(2);
    var firstSourceId = createApplication(firstGroup.lastName());
    var secondSourceId = createApplication(secondGroup.lastName());
    var results =
        race(
            () ->
                link(
                    firstSourceId,
                    firstGroup.leadId(),
                    token(firstGroup.groupId(), firstGroup.version())),
            () ->
                link(
                    secondSourceId,
                    secondGroup.leadId(),
                    token(secondGroup.groupId(), secondGroup.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsOnly(HttpStatus.NO_CONTENT);
    assertThat(countDomainEvents(firstGroup.groupId(), MemberAddedToGroupEvent.class)).isOne();
    assertThat(countDomainEvents(secondGroup.groupId(), MemberAddedToGroupEvent.class)).isOne();
    var firstMembers = List.of(firstGroup.leadId(), firstGroup.memberIds().get(1), firstSourceId);
    var secondMembers =
        List.of(secondGroup.leadId(), secondGroup.memberIds().get(1), secondSourceId);
    awaitFinalGroupState(firstGroup.groupId(), firstMembers, firstMembers, firstGroup.leadId(), 1);
    awaitFinalGroupState(
        secondGroup.groupId(), secondMembers, secondMembers, secondGroup.leadId(), 1);
  }

  @Test
  void givenConcurrentMakeLeadRequests_whenTargetsDiffer_thenOneConflicts() throws Exception {
    var group = createGroup(3);
    var firstTargetId = group.memberIds().get(1);
    var secondTargetId = group.memberIds().get(2);
    var results =
        race(
            () -> makeLead(firstTargetId, token(group.groupId(), group.version())),
            () -> makeLead(secondTargetId, token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isOne();
    awaitFinalGroupState(
        group.groupId(),
        group.memberIds(),
        group.memberIds(),
        results.get(0).getStatusCode() == HttpStatus.NO_CONTENT ? firstTargetId : secondTargetId,
        group.version() + 1);
  }

  @Test
  void givenConcurrentMakeLeadAndUnlink_whenSameAssociate_thenOneConflicts() throws Exception {
    var group = createGroup(3);
    var memberId = group.memberIds().get(1);
    var results =
        race(
            () -> makeLead(memberId, token(group.groupId(), group.version())),
            () -> unlink(memberId, token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(
            countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class)
                + countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class))
        .isOne();
    var successfulLeadChange = results.get(0).getStatusCode().is2xxSuccessful();
    awaitFinalGroupState(
        group.groupId(),
        group.memberIds(),
        successfulLeadChange
            ? group.memberIds()
            : List.of(group.leadId(), group.memberIds().get(2)),
        successfulLeadChange ? memberId : group.leadId(),
        group.version() + 1);
  }

  @Test
  void givenConcurrentUnlinks_whenThreeMemberGroup_thenOneConflicts() throws Exception {
    var group = createGroup(3);
    var results =
        race(
            () -> unlink(group.memberIds().get(1), token(group.groupId(), group.version())),
            () -> unlink(group.memberIds().get(2), token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)).isOne();
    var firstRemoved = results.get(0).getStatusCode() == HttpStatus.NO_CONTENT;
    awaitFinalGroupState(
        group.groupId(),
        group.memberIds(),
        List.of(group.leadId(), group.memberIds().get(firstRemoved ? 2 : 1)),
        group.leadId(),
        group.version() + 1);
  }

  @Test
  void givenConcurrentUnlinkAndLink_whenTwoMemberGroup_thenNoSingletonGroup() throws Exception {
    var group = createGroup(2);
    var unlinkedId = group.memberIds().get(1);
    var newApplicationId = createApplication(group.lastName());
    var results =
        race(
            () -> unlink(unlinkedId, token(group.groupId(), group.version())),
            () -> link(newApplicationId, group.leadId(), token(group.groupId(), group.version())));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .allSatisfy(status -> assertThat(status).isIn(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT));
    assertThat(results.stream().filter(result -> result.getStatusCode().is2xxSuccessful()))
        .hasSize(1);
    assertThat(
            countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class)
                + countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class))
        .isOne();
    if (results.get(0).getStatusCode().is2xxSuccessful()) {
      awaitFinalGroupState(
          group.groupId(),
          List.of(group.leadId(), unlinkedId, newApplicationId),
          List.of(),
          null,
          0);
    } else {
      awaitFinalGroupState(
          group.groupId(),
          List.of(group.leadId(), unlinkedId, newApplicationId),
          List.of(group.leadId(), unlinkedId, newApplicationId),
          group.leadId(),
          group.version() + 1);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3})
  void givenRouteUpdateFails_whenMemberLeaves_thenEventAndRoutesRollBack(int memberCount) {
    var group = createGroup(memberCount);
    UUID removedId = group.memberIds().get(1);
    try {
      jdbcTemplate.execute(
          """
                    CREATE OR REPLACE FUNCTION axon.reject_test_group_route_leave()
                    RETURNS trigger AS $$
                    BEGIN
                      IF OLD.group_id = '%s' AND NEW.group_id IS NULL THEN
                        RAISE EXCEPTION 'forced application group route leave failure';
                      END IF;
                      RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """
              .formatted(group.groupId()));
      jdbcTemplate.execute(
          """
                    CREATE TRIGGER reject_test_group_route_leave
                    BEFORE UPDATE ON axon.application_group_route
                    FOR EACH ROW EXECUTE FUNCTION axon.reject_test_group_route_leave()
                    """);

      assertThatThrownBy(
              () ->
                  commandGateway.sendAndWait(
                      new RemoveApplicationFromLinkedGroupCommand(
                          group.groupId(), removedId, group.version(), Instant.now())))
          .satisfies(
              failure ->
                  assertThat(rootCause(failure))
                      .hasMessageContaining("forced application group route leave failure"));
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER IF EXISTS reject_test_group_route_leave ON axon.application_group_route");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_group_route_leave()");
    }

    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class))
        .isZero();
    assertThat(countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)).isZero();
    assertThat(historyCount(removedId, "APPLICATION_GROUP_LEFT")).isZero();
    awaitFinalGroupState(
        group.groupId(), group.memberIds(), group.memberIds(), group.leadId(), group.version());
  }

  @Test
  void givenRouteUpdateFails_whenAddingMember_thenEventAndRoutesRollBack() {
    var group = createGroup(2);
    var newMemberId = createApplication(group.lastName());
    try {
      jdbcTemplate.execute(
          """
                    CREATE OR REPLACE FUNCTION axon.reject_test_group_route_join()
                    RETURNS trigger AS $$
                    BEGIN
                      IF OLD.group_id IS NULL AND NEW.group_id = '%s' THEN
                        RAISE EXCEPTION 'forced application group route join failure';
                      END IF;
                      RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """
              .formatted(group.groupId()));
      jdbcTemplate.execute(
          """
                    CREATE TRIGGER reject_test_group_route_join
                    BEFORE UPDATE ON axon.application_group_route
                    FOR EACH ROW EXECUTE FUNCTION axon.reject_test_group_route_join()
                    """);

      assertThatThrownBy(
              () ->
                  commandGateway.sendAndWait(
                      new AddApplicationToLinkedGroupCommand(
                          group.groupId(), newMemberId, group.version(), Instant.now())))
          .satisfies(
              failure ->
                  assertThat(rootCause(failure))
                      .hasMessageContaining("forced application group route join failure"));
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER IF EXISTS reject_test_group_route_join ON axon.application_group_route");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_group_route_join()");
    }

    assertThat(countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class)).isZero();
    assertThat(historyCount(newMemberId, "APPLICATION_GROUP_JOINED")).isZero();
    awaitFinalGroupState(
        group.groupId(),
        List.of(group.leadId(), group.memberIds().get(1), newMemberId),
        group.memberIds(),
        group.leadId(),
        group.version());
  }

  private Group createGroup(int memberCount) {
    String lastName = uniqueLastName();
    List<UUID> memberIds = new ArrayList<>();
    for (int index = 0; index < memberCount; index++) {
      memberIds.add(createApplication(lastName));
    }
    UUID leadId = memberIds.getFirst();
    assertThat(link(memberIds.get(1), leadId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var groupId = awaitRoute(leadId, ApplicationGroupRouteKind.LINKED_GROUP).getGroupId();
    for (int index = 2; index < memberCount; index++) {
      assertThat(
              link(memberIds.get(index), leadId, token(groupId, (long) index - 2)).getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);
    }
    awaitGroupProjection(groupId, memberIds);
    return new Group(groupId, leadId, memberIds, memberCount - 2L, lastName);
  }

  private UUID createApplication(String lastName) {
    UUID applicationId = UUID.randomUUID();
    var request = validCreateApplicationRequest(applicationId, UUID.randomUUID());
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<?, ?> originalClient = (Map<?, ?>) content.get("client");
    Map<String, Object> client = new HashMap<>();
    originalClient.forEach((key, value) -> client.put(key.toString(), value));
    client.put("lastName", lastName);
    content.put("client", client);
    Map<?, ?> originalProvider = (Map<?, ?>) content.get("provider");
    Map<String, Object> provider = new HashMap<>();
    originalProvider.forEach((key, value) -> provider.put(key.toString(), value));
    provider.put("officeCode", "1A001B");
    content.put("provider", provider);
    request.setApplicationContent(content);

    var response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    projectionAwaiter.awaitApplication(applicationId);
    awaitRoute(applicationId, ApplicationGroupRouteKind.STANDALONE);
    return applicationId;
  }

  private ResponseEntity<Void> link(UUID sourceId, UUID targetId, String token) {
    var request = new ApplicationLinkRequest(targetId, ApplicationLinkType.FAMILY);
    request.setLinkedGroupVersion(token);
    return restTemplate.exchange(
        "/api/v0/applications/" + sourceId + "/link",
        HttpMethod.POST,
        new HttpEntity<>(request, headers()),
        Void.class);
  }

  private ResponseEntity<String> groupChangeWithBody(
      UUID applicationId, String token, String operation) {
    return restTemplate.exchange(
        "/api/v0/applications/" + applicationId + "/" + operation,
        HttpMethod.POST,
        new HttpEntity<>(new LinkedGroupChangeRequest(token), headers()),
        String.class);
  }

  private ResponseEntity<Void> makeLead(UUID applicationId, String token) {
    return groupChange(applicationId, token, "make-lead");
  }

  private ResponseEntity<Void> unlink(UUID applicationId, String token) {
    return groupChange(applicationId, token, "unlink");
  }

  private ResponseEntity<Void> groupChange(UUID applicationId, String token, String operation) {
    return restTemplate.exchange(
        "/api/v0/applications/" + applicationId + "/" + operation,
        HttpMethod.POST,
        new HttpEntity<>(new LinkedGroupChangeRequest(token), headers()),
        Void.class);
  }

  private ApplicationResponse application(UUID applicationId) {
    return restTemplate
        .exchange(
            "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationResponse.class)
        .getBody();
  }

  private ApplicationGroupRoute route(UUID applicationId) {
    return routeRepository.findById(applicationId).orElseThrow();
  }

  private ApplicationGroupRoute awaitRoute(
      UUID applicationId, ApplicationGroupRouteKind expectedKind) {
    return await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> routeRepository.findById(applicationId).orElse(null),
            found -> found != null && found.getRouteKind() == expectedKind);
  }

  private void awaitStandalone(UUID applicationId) {
    awaitRoute(applicationId, ApplicationGroupRouteKind.STANDALONE);
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              var response = application(applicationId);
              assertThat(response.getLinkedGroupVersion()).isNull();
              assertThat(response.getLinkedApplications()).isEmpty();
            });
  }

  private Map<UUID, RouteState> routeMembership(List<UUID> applicationIds) {
    return routeRepository.findAllById(applicationIds).stream()
        .collect(
            Collectors.toMap(
                ApplicationGroupRoute::getApplicationId,
                route ->
                    new RouteState(
                        route.getRouteKind(),
                        route.getGroupId(),
                        route.getRouteVersion(),
                        route.getUpdatedAt())));
  }

  private LinkedApplicationGroupReadModel group(UUID groupId) {
    return groupRepository.findById(groupId).orElseThrow();
  }

  private void awaitGroupProjection(UUID groupId, List<UUID> memberIds) {
    await()
        .alias("group projection for " + groupId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(groupRepository.findById(groupId))
                    .hasValueSatisfying(
                        projected ->
                            assertThat(projected.getMemberIds())
                                .containsExactlyInAnyOrderElementsOf(memberIds)));
    awaitGroupVersion(memberIds.getFirst(), memberIds.size() - 2L);
  }

  private void awaitGroupMembers(UUID groupId, UUID... memberIds) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(groupRepository.findById(groupId))
                    .hasValueSatisfying(
                        projected ->
                            assertThat(projected.getMemberIds())
                                .containsExactlyInAnyOrder(memberIds)));
  }

  private void awaitGroupVersion(UUID applicationId, long expectedVersion) {
    await()
        .alias("linked group version " + expectedVersion + " for " + applicationId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(application(applicationId).getLinkedGroupVersion())
                    .isEqualTo(token(route(applicationId).getGroupId(), expectedVersion)));
  }

  private void awaitMembership(List<UUID> memberIds, UUID leadId) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                memberIds.forEach(
                    memberId -> {
                      var response = application(memberId);
                      assertThat(response.getIsLead()).isEqualTo(memberId.equals(leadId));
                      assertThat(response.getLinkedApplications())
                          .extracting(LinkedApplicationSummaryResponse::getApplicationId)
                          .containsExactlyInAnyOrderElementsOf(
                              memberIds.stream().filter(id -> !id.equals(memberId)).toList());
                    }));
  }

  private void awaitFinalGroupState(
      UUID groupId,
      List<UUID> allParticipants,
      List<UUID> expectedMembers,
      UUID expectedLead,
      long expectedVersion) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              if (expectedMembers.isEmpty()) {
                assertThat(groupRepository.findById(groupId)).isEmpty();
              } else {
                assertThat(groupRepository.findById(groupId))
                    .hasValueSatisfying(
                        projected -> {
                          assertThat(projected.getMemberIds())
                              .containsExactlyInAnyOrderElementsOf(expectedMembers);
                          assertThat(projected.getLeadApplicationId()).isEqualTo(expectedLead);
                          assertThat(projected.getVersion()).isEqualTo(expectedVersion);
                        });
              }
              for (var applicationId : allParticipants) {
                var isMember = expectedMembers.contains(applicationId);
                var foundRoute = route(applicationId);
                var response = application(applicationId);
                assertThat(foundRoute.getRouteKind())
                    .isEqualTo(
                        isMember
                            ? ApplicationGroupRouteKind.LINKED_GROUP
                            : ApplicationGroupRouteKind.STANDALONE);
                assertThat(foundRoute.getGroupId()).isEqualTo(isMember ? groupId : null);
                assertThat(response.getLinkedGroupVersion())
                    .isEqualTo(isMember ? token(groupId, expectedVersion) : null);
                assertThat(response.getLinkedApplications())
                    .extracting(LinkedApplicationSummaryResponse::getApplicationId)
                    .containsExactlyInAnyOrderElementsOf(
                        isMember
                            ? expectedMembers.stream()
                                .filter(id -> !id.equals(applicationId))
                                .toList()
                            : List.of());
                assertThat(response.getIsLead())
                    .isEqualTo(isMember && applicationId.equals(expectedLead));
              }
              assertNoSingleMemberGroups();
            });
  }

  private void awaitGroupDeleted(UUID groupId) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> groupRepository.findById(groupId).isEmpty());
  }

  private long countDomainEvents(UUID groupId, Class<?> eventType) {
    return jdbcTemplate.queryForObject(
        """
                SELECT COUNT(*) FROM axon.domain_event_entry
                WHERE aggregate_identifier = ? AND payload_type = ?
                """,
        Long.class,
        groupId.toString(),
        eventType.getName());
  }

  private long countAllDomainEvents(Class<?> eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.domain_event_entry WHERE payload_type = ?",
        Long.class,
        eventType.getName());
  }

  private static String token(UUID groupId, long version) {
    return VersionToken.linkedGroup(groupId, version).encode();
  }

  private long historyCount(UUID applicationId, String eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.application_history WHERE application_id = ? AND event_type = ?",
        Long.class,
        applicationId,
        eventType);
  }

  private void awaitHistory(UUID applicationId, String eventType) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> historyCount(applicationId, eventType) > 0);
  }

  private List<ResponseEntity<Void>> race(
      Supplier<ResponseEntity<Void>> first, Supplier<ResponseEntity<Void>> second)
      throws Exception {
    var barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var firstResult = concurrent(executor, barrier, first);
      var secondResult = concurrent(executor, barrier, second);
      return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  private CompletableFuture<ResponseEntity<Void>> concurrent(
      ExecutorService executor, CyclicBarrier barrier, Supplier<ResponseEntity<Void>> operation) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            barrier.await(10, TimeUnit.SECONDS);
            return operation.get();
          } catch (Exception failure) {
            throw new RuntimeException(failure);
          }
        },
        executor);
  }

  private void assertNoSingleMemberGroups() {
    assertThat(
            jdbcTemplate.queryForList(
                """
                        SELECT group_id FROM axon.application_group_route
                        WHERE group_id IS NOT NULL
                        GROUP BY group_id HAVING count(*) = 1
                        """,
                UUID.class))
        .isEmpty();
  }

  private String uniqueLastName() {
    return "Membership" + UUID.randomUUID().toString().replace("-", "");
  }

  private HttpHeaders headers() {
    var headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }

  private Throwable rootCause(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private record Group(
      UUID groupId, UUID leadId, List<UUID> memberIds, long version, String lastName) {}

  private record RouteState(
      ApplicationGroupRouteKind routeKind, UUID groupId, long routeVersion, Instant updatedAt) {}
}
