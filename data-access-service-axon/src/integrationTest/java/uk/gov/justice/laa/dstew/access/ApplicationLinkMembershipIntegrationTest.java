package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
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
import org.junit.jupiter.api.Test;
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

    assertThat(makeLead(newLeadId, group.version()).getStatusCode())
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

    assertThat(makeLead(group.leadId(), 99).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isEqualTo(eventCount);
  }

  @Test
  void givenStandaloneApplication_whenMadeLead_thenConflict() {
    UUID applicationId = createApplication(uniqueLastName());

    assertThat(makeLead(applicationId, 0).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void givenUnknownApplication_whenMadeLead_thenNotFound() {
    assertThat(makeLead(UUID.randomUUID(), 0).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenThreeMemberGroup_whenAssociateUnlinked_thenOnlyAssociateLeaves() {
    var group = createGroup(3);
    UUID removedId = group.memberIds().get(1);

    assertThat(unlink(removedId, group.version()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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

    assertThat(unlink(removedId, group.version()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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

    assertThat(unlink(group.leadId(), group.version()).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(route(group.leadId()).getRouteKind())
        .isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
  }

  @Test
  void givenStandaloneApplication_whenUnlinked_thenConflict() {
    UUID applicationId = createApplication(uniqueLastName());

    assertThat(unlink(applicationId, 0).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void givenUnknownApplication_whenUnlinked_thenNotFound() {
    assertThat(unlink(UUID.randomUUID(), 0).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenUnlinkedApplication_whenLinkedAgain_thenJoinsNewGroup() {
    var group = createGroup(3);
    UUID removedId = group.memberIds().get(1);
    UUID newTargetId = createApplication(group.lastName());
    assertThat(unlink(removedId, group.version()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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

    assertThat(makeLead(newLeadId, group.version()).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(newLeadId, group.version() + 1);

    assertThat(unlink(group.leadId(), group.version() + 1).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitStandalone(group.leadId());
    assertThat(route(newLeadId).getGroupId()).isEqualTo(group.groupId());
  }

  @Test
  void givenStaleVersion_whenMadeLead_thenConflict() {
    var group = createGroup(3);
    UUID additionalMemberId = createApplication(group.lastName());
    assertThat(link(additionalMemberId, group.leadId(), group.version()).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(additionalMemberId, group.version() + 1);
    long leadChangedCount =
        countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class);

    var conflict = groupChangeWithBody(group.memberIds().get(1), group.version(), "make-lead");
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody())
        .contains(
            "Linked group of application "
                + group.memberIds().get(1)
                + " has changed since version "
                + group.version());
    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isEqualTo(leadChangedCount);
    assertThat(route(group.memberIds().get(1)).getGroupId()).isEqualTo(group.groupId());
  }

  @Test
  void givenStaleVersion_whenUnlinked_thenConflict() {
    var group = createGroup(3);
    UUID additionalMemberId = createApplication(group.lastName());
    assertThat(link(additionalMemberId, group.leadId(), group.version()).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(additionalMemberId, group.version() + 1);
    long removalEventCount =
        countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)
            + countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class);

    var conflict = groupChangeWithBody(group.memberIds().get(1), group.version(), "unlink");
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody())
        .contains(
            "Linked group of application "
                + group.memberIds().get(1)
                + " has changed since version "
                + group.version());
    assertThat(
            countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)
                + countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class))
        .isEqualTo(removalEventCount);
    assertThat(route(group.memberIds().get(1)).getGroupId()).isEqualTo(group.groupId());
  }

  @Test
  void givenLinkedApplication_whenFetched_thenExposesLinkedGroupVersion() {
    var group = createGroup(2);
    assertThat(application(group.leadId()).getLinkedGroupVersion()).isZero();

    UUID thirdMemberId = createApplication(group.lastName());
    List<UUID> allMemberIds = new ArrayList<>(group.memberIds());
    allMemberIds.add(thirdMemberId);
    assertThat(link(thirdMemberId, group.leadId(), 0L).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(thirdMemberId, 1);
    assertThat(application(thirdMemberId).getLinkedGroupVersion()).isEqualTo(1L);

    UUID newLeadId = group.memberIds().get(1);
    assertThat(makeLead(newLeadId, 1).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    awaitGroupVersion(newLeadId, 2);
    assertThat(application(newLeadId).getLinkedGroupVersion()).isEqualTo(2L);

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
                        assertThat(summary.getLinkedGroupVersion()).isEqualTo(2L);
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

    assertThat(unlink(thirdMemberId, 2).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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

  @Test
  void givenMissingVersion_whenMadeLead_thenBadRequest() {
    UUID applicationId = createApplication(uniqueLastName());
    var rawHeaders = headers();
    rawHeaders.setContentType(MediaType.APPLICATION_JSON);

    var response =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/make-lead",
            HttpMethod.POST,
            new HttpEntity<>("{}", rawHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void givenConcurrentMakeLeadRequests_whenTargetsDiffer_thenOneConflicts() throws Exception {
    var group = createGroup(3);
    var firstTargetId = group.memberIds().get(1);
    var secondTargetId = group.memberIds().get(2);
    var results =
        race(
            () -> makeLead(firstTargetId, group.version()),
            () -> makeLead(secondTargetId, group.version()));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class))
        .isOne();
    awaitMembership(
        group.memberIds(),
        results.get(0).getStatusCode() == HttpStatus.NO_CONTENT ? firstTargetId : secondTargetId);
    assertNoSingleMemberGroups();
  }

  @Test
  void givenConcurrentMakeLeadAndUnlink_whenSameAssociate_thenOneConflicts() throws Exception {
    var group = createGroup(3);
    var memberId = group.memberIds().get(1);
    var results =
        race(() -> makeLead(memberId, group.version()), () -> unlink(memberId, group.version()));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(
            countDomainEvents(group.groupId(), LinkedApplicationGroupLeadChangedEvent.class)
                + countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class))
        .isOne();
    var successfulLeadChange = results.get(0).getStatusCode().is2xxSuccessful();
    assertThat(route(memberId).getRouteKind())
        .isEqualTo(
            successfulLeadChange
                ? ApplicationGroupRouteKind.LINKED_GROUP
                : ApplicationGroupRouteKind.STANDALONE);
    if (successfulLeadChange) {
      awaitMembership(group.memberIds(), memberId);
    }
    assertNoSingleMemberGroups();
  }

  @Test
  void givenConcurrentUnlinks_whenThreeMemberGroup_thenOneConflicts() throws Exception {
    var group = createGroup(3);
    var results =
        race(
            () -> unlink(group.memberIds().get(1), group.version()),
            () -> unlink(group.memberIds().get(2), group.version()));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    assertThat(countDomainEvents(group.groupId(), MemberRemovedFromGroupEvent.class)).isOne();
    assertThat(route(group.leadId()).getRouteKind())
        .isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(route(group.memberIds().get(1)).getRouteKind())
        .isIn(ApplicationGroupRouteKind.LINKED_GROUP, ApplicationGroupRouteKind.STANDALONE);
    assertThat(route(group.memberIds().get(2)).getRouteKind())
        .isIn(ApplicationGroupRouteKind.LINKED_GROUP, ApplicationGroupRouteKind.STANDALONE);
    assertThat(
            List.of(route(group.memberIds().get(1)), route(group.memberIds().get(2))).stream()
                .filter(route -> route.getRouteKind() == ApplicationGroupRouteKind.STANDALONE))
        .hasSize(1);
    awaitGroupMembers(
        group.groupId(),
        group.leadId(),
        route(group.memberIds().get(1)).getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP
            ? group.memberIds().get(1)
            : group.memberIds().get(2));
    assertNoSingleMemberGroups();
  }

  @Test
  void givenConcurrentUnlinkAndLink_whenTwoMemberGroup_thenNoSingletonGroup() throws Exception {
    var group = createGroup(2);
    var unlinkedId = group.memberIds().get(1);
    var newApplicationId = createApplication(group.lastName());
    var results =
        race(
            () -> unlink(unlinkedId, group.version()),
            () -> link(newApplicationId, group.leadId(), group.version()));

    assertThat(results)
        .extracting(ResponseEntity::getStatusCode)
        .allSatisfy(status -> assertThat(status).isIn(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT));
    assertThat(results.stream().filter(result -> result.getStatusCode().is2xxSuccessful()))
        .hasSize(1);
    assertThat(
            countDomainEvents(group.groupId(), LinkedApplicationGroupDissolvedEvent.class)
                + countDomainEvents(group.groupId(), MemberAddedToGroupEvent.class))
        .isOne();
    var leadRoute = route(group.leadId());
    var removedRoute = route(unlinkedId);
    var linkedRoute = route(newApplicationId);
    if (results.get(0).getStatusCode().is2xxSuccessful()) {
      assertThat(List.of(leadRoute, removedRoute, linkedRoute))
          .allSatisfy(
              route ->
                  assertThat(route.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.STANDALONE));
    } else {
      assertThat(List.of(leadRoute, removedRoute, linkedRoute))
          .allSatisfy(
              route -> {
                assertThat(route.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
                assertThat(route.getGroupId()).isEqualTo(group.groupId());
              });
    }
    assertNoSingleMemberGroups();
  }

  @Test
  void givenRouteUpdateFails_whenGroupDissolved_thenEventAndRoutesRollBack() {
    var group = createGroup(2);
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
    assertThat(groupRepository.findById(group.groupId())).isPresent();
    assertThat(route(group.leadId()).getRouteKind())
        .isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(route(removedId).getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
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
      assertThat(link(memberIds.get(index), leadId, (long) index - 2).getStatusCode())
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

  private ResponseEntity<Void> link(UUID sourceId, UUID targetId, Long version) {
    var request = new ApplicationLinkRequest(targetId, ApplicationLinkType.FAMILY);
    request.setLinkedGroupVersion(version);
    return restTemplate.exchange(
        "/api/v0/applications/" + sourceId + "/link",
        HttpMethod.POST,
        new HttpEntity<>(request, headers()),
        Void.class);
  }

  private ResponseEntity<String> groupChangeWithBody(
      UUID applicationId, long version, String operation) {
    return restTemplate.exchange(
        "/api/v0/applications/" + applicationId + "/" + operation,
        HttpMethod.POST,
        new HttpEntity<>(new LinkedGroupChangeRequest(version), headers()),
        String.class);
  }

  private ResponseEntity<Void> makeLead(UUID applicationId, long version) {
    return groupChange(applicationId, version, "make-lead");
  }

  private ResponseEntity<Void> unlink(UUID applicationId, long version) {
    return groupChange(applicationId, version, "unlink");
  }

  private ResponseEntity<Void> groupChange(UUID applicationId, long version, String operation) {
    return restTemplate.exchange(
        "/api/v0/applications/" + applicationId + "/" + operation,
        HttpMethod.POST,
        new HttpEntity<>(new LinkedGroupChangeRequest(version), headers()),
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
                    .isEqualTo(expectedVersion));
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
