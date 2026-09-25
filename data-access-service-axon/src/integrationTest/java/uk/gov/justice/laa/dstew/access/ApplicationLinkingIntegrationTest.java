package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.EstablishLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRoute;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
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
public class ApplicationLinkingIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private CommandGateway commandGateway;

  @Autowired private QueryGateway queryGateway;

  @Autowired private ApplicationGroupRouteRepository applicationGroupRouteRepository;

  @Autowired private LinkedApplicationGroupReadRepository groupReadRepository;

  private ProjectionAwaiter projectionAwaiter;

  @PostConstruct
  void initialiseProjectionAwaiter() {
    projectionAwaiter = new ProjectionAwaiter(queryGateway);
  }

  @Test
  void givenTwoStandaloneApplications_whenApplicationsAreLinked_thenLinkIsSuccessful() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    createApplication(sourceApplicationId, clientLastName);
    createApplication(targetApplicationId, clientLastName);
    long createdEventCount = countDomainEvents(LinkedApplicationGroupCreatedEvent.class.getName());

    ResponseEntity<Void> response = linkApplication(sourceApplicationId, targetApplicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    var routesByApplicationId = awaitRoutes(sourceApplicationId, targetApplicationId);
    ApplicationGroupRoute targetRoute = routesByApplicationId.get(targetApplicationId);
    ApplicationGroupRoute sourceRoute = routesByApplicationId.get(sourceApplicationId);
    UUID groupId = targetRoute.getGroupId();
    assertThat(targetRoute.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(sourceRoute.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(groupId).isNotNull().isNotIn(sourceApplicationId, targetApplicationId);
    assertThat(sourceRoute.getGroupId()).isEqualTo(groupId);
    assertThat(countDomainEvents(LinkedApplicationGroupCreatedEvent.class.getName()))
        .isEqualTo(createdEventCount + 1);
    assertThat(countDomainEvents(groupId, LinkedApplicationGroupCreatedEvent.class.getName()))
        .isOne();

    LinkedApplicationGroupReadModel group =
        awaitGroupProjection(
            groupId, targetApplicationId, targetApplicationId, sourceApplicationId);
    assertThat(group.getMemberIds())
        .containsExactlyInAnyOrder(targetApplicationId, sourceApplicationId);
    assertThat(group.getLeadApplicationId()).isEqualTo(targetApplicationId);

    var applicationSummaries =
        awaitApplicationSummaryGroup(clientLastName, targetApplicationId, sourceApplicationId);
    assertApplicationSummaryGroup(applicationSummaries, targetApplicationId, sourceApplicationId);
  }

  @Test
  void givenApplicationsFromDifferentOffices_whenApplicationsAreLinked_thenRequestIsRejected() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    createApplication(sourceApplicationId, clientLastName, "1A001B");
    createApplication(targetApplicationId, clientLastName, "2B002C");
    long groupEventCount = countGroupEvents();

    ResponseEntity<Void> response = linkApplication(sourceApplicationId, targetApplicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(countGroupEvents()).isEqualTo(groupEventCount);
    assertStandaloneRoute(sourceApplicationId);
    assertStandaloneRoute(targetApplicationId);
  }

  @Test
  void givenApplicationWithoutOfficeCode_whenApplicationIsCreated_thenRequestIsRejected() {
    UUID applicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    var request = createApplicationRequest(applicationId, clientLastName);
    var applicationContent = new HashMap<>(request.getApplicationContent());
    var originalProvider = (Map<?, ?>) applicationContent.get("provider");
    var provider = new HashMap<String, Object>();
    originalProvider.forEach((key, value) -> provider.put(key.toString(), value));
    provider.remove("officeCode");
    applicationContent.put("provider", provider);
    request.setApplicationContent(applicationContent);

    var response = post(request, headers());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(countRoutes(applicationId)).isZero();
  }

  @ParameterizedTest
  @MethodSource("invalidOfficeCodeCombinations")
  void givenApplicationsWithMissingOfficeCode_whenApplicationsAreLinked_thenRequestIsRejected(
      String sourceOfficeCode, String targetOfficeCode) {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    createApplication(sourceApplicationId, clientLastName, sourceOfficeCode);
    createApplication(targetApplicationId, clientLastName, targetOfficeCode);
    long groupEventCount = countGroupEvents();

    ResponseEntity<Void> response = linkApplication(sourceApplicationId, targetApplicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(countGroupEvents()).isEqualTo(groupEventCount);
    assertStandaloneRoute(sourceApplicationId);
    assertStandaloneRoute(targetApplicationId);
  }

  private static Stream<Arguments> invalidOfficeCodeCombinations() {
    return Stream.of(
        Arguments.of("1A001B", " "), Arguments.of(" ", "1A001B"), Arguments.of(" ", " "));
  }

  @Test
  void
      givenStandaloneSourceAndGroupedTarget_whenApplicationsAreLinked_thenSourceJoinsTargetGroup() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    UUID existingMemberApplicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    createApplication(sourceApplicationId, clientLastName);
    createApplication(targetApplicationId, clientLastName);
    createApplication(existingMemberApplicationId, clientLastName);
    assertThat(linkApplication(existingMemberApplicationId, targetApplicationId).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    UUID groupId =
        awaitRoutes(targetApplicationId, existingMemberApplicationId)
            .get(targetApplicationId)
            .getGroupId();
    awaitGroupProjection(
        groupId, targetApplicationId, targetApplicationId, existingMemberApplicationId);
    long memberAddedEventCount =
        countDomainEvents(groupId, MemberAddedToGroupEvent.class.getName());

    ResponseEntity<Void> response = linkApplication(sourceApplicationId, targetApplicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(countDomainEvents(groupId, MemberAddedToGroupEvent.class.getName()))
        .isEqualTo(memberAddedEventCount + 1);
    assertRoutesInGroup(
        groupId, targetApplicationId, existingMemberApplicationId, sourceApplicationId);
    LinkedApplicationGroupReadModel group =
        awaitGroupProjection(
            groupId,
            targetApplicationId,
            targetApplicationId,
            existingMemberApplicationId,
            sourceApplicationId);
    assertThat(group.getLeadApplicationId()).isEqualTo(targetApplicationId);
    assertThat(group.getMemberIds())
        .containsExactlyInAnyOrder(
            targetApplicationId, existingMemberApplicationId, sourceApplicationId);
  }

  @Test
  void givenSameSourceAndTarget_whenApplicationsAreLinked_thenRequestIsRejected() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId, uniqueClientLastName());
    long groupEventCount = countGroupEvents();

    ResponseEntity<Void> response = linkApplication(applicationId, applicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(countGroupEvents()).isEqualTo(groupEventCount);
    assertStandaloneRoute(applicationId);
  }

  @Test
  void givenSourceAlreadyBelongsToAnotherGroup_whenApplicationsAreLinked_thenRequestConflicts() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID originalLeadApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    UUID targetLeadApplicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    createApplication(sourceApplicationId, clientLastName);
    createApplication(originalLeadApplicationId, clientLastName);
    createApplication(targetApplicationId, clientLastName);
    createApplication(targetLeadApplicationId, clientLastName);
    assertThat(linkApplication(sourceApplicationId, originalLeadApplicationId).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    UUID originalGroupId =
        awaitRoutes(sourceApplicationId, originalLeadApplicationId)
            .get(sourceApplicationId)
            .getGroupId();
    awaitGroupProjection(
        originalGroupId, originalLeadApplicationId, originalLeadApplicationId, sourceApplicationId);
    assertThat(linkApplication(targetApplicationId, targetLeadApplicationId).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    UUID targetGroupId =
        awaitRoutes(targetApplicationId, targetLeadApplicationId)
            .get(targetApplicationId)
            .getGroupId();
    awaitGroupProjection(
        targetGroupId, targetLeadApplicationId, targetLeadApplicationId, targetApplicationId);
    long groupEventCount = countGroupEvents();

    ResponseEntity<Void> response = linkApplication(sourceApplicationId, targetApplicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(countGroupEvents()).isEqualTo(groupEventCount);
    assertRoutesInGroup(originalGroupId, originalLeadApplicationId, sourceApplicationId);
    assertRoutesInGroup(targetGroupId, targetLeadApplicationId, targetApplicationId);
    assertGroupMembers(
        awaitGroupProjection(
            originalGroupId,
            originalLeadApplicationId,
            originalLeadApplicationId,
            sourceApplicationId),
        originalLeadApplicationId,
        originalLeadApplicationId,
        sourceApplicationId);
    assertGroupMembers(
        awaitGroupProjection(
            targetGroupId, targetLeadApplicationId, targetLeadApplicationId, targetApplicationId),
        targetLeadApplicationId,
        targetLeadApplicationId,
        targetApplicationId);
  }

  @Test
  void
      givenRepeatedSourceToTargetLinkRequest_whenApplicationsAreLinked_thenSecondRequestIsIdempotent() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    createApplication(sourceApplicationId, clientLastName);
    createApplication(targetApplicationId, clientLastName);

    ResponseEntity<Void> firstResponse = linkApplication(sourceApplicationId, targetApplicationId);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    UUID groupId =
        awaitRoutes(sourceApplicationId, targetApplicationId).get(targetApplicationId).getGroupId();
    LinkedApplicationGroupReadModel initialGroup =
        awaitGroupProjection(
            groupId, targetApplicationId, targetApplicationId, sourceApplicationId);
    long createdEventCount =
        countDomainEvents(groupId, LinkedApplicationGroupCreatedEvent.class.getName());
    long memberAddedEventCount =
        countDomainEvents(groupId, MemberAddedToGroupEvent.class.getName());

    ResponseEntity<Void> secondResponse = linkApplication(sourceApplicationId, targetApplicationId);

    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(countDomainEvents(groupId, LinkedApplicationGroupCreatedEvent.class.getName()))
        .isEqualTo(createdEventCount);
    assertThat(countDomainEvents(groupId, MemberAddedToGroupEvent.class.getName()))
        .isEqualTo(memberAddedEventCount);
    LinkedApplicationGroupReadModel finalGroup =
        awaitGroupProjection(
            groupId, targetApplicationId, targetApplicationId, sourceApplicationId);
    assertThat(finalGroup.getMemberIds()).containsExactlyElementsOf(initialGroup.getMemberIds());
    assertThat(new HashSet<>(finalGroup.getMemberIds())).hasSameSizeAs(finalGroup.getMemberIds());
  }

  @Test
  void givenUnsupportedRawJsonLinkType_whenApplicationsAreLinked_thenRequestIsRejected() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    createApplication(sourceApplicationId, clientLastName);
    createApplication(targetApplicationId, clientLastName);
    long groupEventCount = countGroupEvents();
    long groupProjectionCount = groupReadRepository.count();
    String requestBody =
        """
                {
                  "applicationId": "%s",
                  "linkType": "OTHER"
                }
                """
            .formatted(targetApplicationId);

    ResponseEntity<String> response = linkApplicationWithRawJson(sourceApplicationId, requestBody);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(countGroupEvents()).isEqualTo(groupEventCount);
    assertThat(groupReadRepository.count()).isEqualTo(groupProjectionCount);
    assertStandaloneRoute(sourceApplicationId);
    assertStandaloneRoute(targetApplicationId);
  }

  @Test
  void givenMissingSourceApplication_whenApplicationsAreLinked_thenRequestReturnsNotFound() {
    UUID missingSourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    createApplication(targetApplicationId, uniqueClientLastName());
    long groupEventCount = countGroupEvents();

    ResponseEntity<Void> response =
        linkApplication(missingSourceApplicationId, targetApplicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(countGroupEvents()).isEqualTo(groupEventCount);
    assertStandaloneRoute(targetApplicationId);
  }

  @Test
  void givenMissingTargetApplication_whenApplicationsAreLinked_thenRequestReturnsNotFound() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID missingTargetApplicationId = UUID.randomUUID();
    createApplication(sourceApplicationId, uniqueClientLastName());
    long groupEventCount = countGroupEvents();

    ResponseEntity<Void> response =
        linkApplication(sourceApplicationId, missingTargetApplicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(countGroupEvents()).isEqualTo(groupEventCount);
    assertStandaloneRoute(sourceApplicationId);
  }

  @Test
  void
      givenConcurrentRequestsLinkingOneSourceToDifferentTargetGroups_whenApplicationsAreLinked_thenOneSucceedsAndOneConflicts()
          throws Exception {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID firstTargetApplicationId = UUID.randomUUID();
    UUID firstExistingMemberApplicationId = UUID.randomUUID();
    UUID secondTargetApplicationId = UUID.randomUUID();
    UUID secondExistingMemberApplicationId = UUID.randomUUID();
    String clientLastName = uniqueClientLastName();
    createApplication(sourceApplicationId, clientLastName);
    createApplication(firstTargetApplicationId, clientLastName);
    createApplication(firstExistingMemberApplicationId, clientLastName);
    createApplication(secondTargetApplicationId, clientLastName);
    createApplication(secondExistingMemberApplicationId, clientLastName);
    assertThat(
            linkApplication(firstExistingMemberApplicationId, firstTargetApplicationId)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    UUID firstGroupId =
        awaitRoutes(firstTargetApplicationId, firstExistingMemberApplicationId)
            .get(firstTargetApplicationId)
            .getGroupId();
    awaitGroupProjection(
        firstGroupId,
        firstTargetApplicationId,
        firstTargetApplicationId,
        firstExistingMemberApplicationId);
    assertThat(
            linkApplication(secondExistingMemberApplicationId, secondTargetApplicationId)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    UUID secondGroupId =
        awaitRoutes(secondTargetApplicationId, secondExistingMemberApplicationId)
            .get(secondTargetApplicationId)
            .getGroupId();
    awaitGroupProjection(
        secondGroupId,
        secondTargetApplicationId,
        secondTargetApplicationId,
        secondExistingMemberApplicationId);
    long memberAddedEventCount =
        countDomainEvents(firstGroupId, MemberAddedToGroupEvent.class.getName())
            + countDomainEvents(secondGroupId, MemberAddedToGroupEvent.class.getName());

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<ResponseEntity<Void>> first =
          concurrentLink(executor, barrier, sourceApplicationId, firstTargetApplicationId);
      CompletableFuture<ResponseEntity<Void>> second =
          concurrentLink(executor, barrier, sourceApplicationId, secondTargetApplicationId);

      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .extracting(ResponseEntity::getStatusCode)
          .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
    } finally {
      executor.shutdown();
    }

    ApplicationGroupRoute sourceRoute =
        awaitRoute(sourceApplicationId, ApplicationGroupRouteKind.LINKED_GROUP);
    assertThat(sourceRoute.getGroupId()).isIn(firstGroupId, secondGroupId);
    assertThat(countRoutes(sourceApplicationId)).isOne();
    assertThat(
            countDomainEvents(firstGroupId, MemberAddedToGroupEvent.class.getName())
                + countDomainEvents(secondGroupId, MemberAddedToGroupEvent.class.getName()))
        .isEqualTo(memberAddedEventCount + 1);
    if (sourceRoute.getGroupId().equals(firstGroupId)) {
      assertGroupMembers(
          awaitGroupProjection(
              firstGroupId,
              firstTargetApplicationId,
              firstTargetApplicationId,
              firstExistingMemberApplicationId,
              sourceApplicationId),
          firstTargetApplicationId,
          firstTargetApplicationId,
          firstExistingMemberApplicationId,
          sourceApplicationId);
      assertGroupMembers(
          awaitGroupProjection(
              secondGroupId,
              secondTargetApplicationId,
              secondTargetApplicationId,
              secondExistingMemberApplicationId),
          secondTargetApplicationId,
          secondTargetApplicationId,
          secondExistingMemberApplicationId);
    } else {
      assertGroupMembers(
          awaitGroupProjection(
              firstGroupId,
              firstTargetApplicationId,
              firstTargetApplicationId,
              firstExistingMemberApplicationId),
          firstTargetApplicationId,
          firstTargetApplicationId,
          firstExistingMemberApplicationId);
      assertGroupMembers(
          awaitGroupProjection(
              secondGroupId,
              secondTargetApplicationId,
              secondTargetApplicationId,
              secondExistingMemberApplicationId,
              sourceApplicationId),
          secondTargetApplicationId,
          secondTargetApplicationId,
          secondExistingMemberApplicationId,
          sourceApplicationId);
    }
  }

  @Test
  void givenRouteUpdateFails_whenLinkedGroupCommandIsDispatched_thenEventAndRoutesRollBack() {
    UUID groupId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    UUID memberApplicationId = UUID.randomUUID();
    var memberApplicationIds = List.of(leadApplicationId, memberApplicationId);
    String clientLastName = uniqueClientLastName();
    createApplication(leadApplicationId, clientLastName);
    createApplication(memberApplicationId, clientLastName);
    var command =
        new EstablishLinkedApplicationGroupCommand(
            groupId, leadApplicationId, memberApplicationIds, Instant.now());

    try {
      jdbcTemplate.execute(
          """
                    CREATE OR REPLACE FUNCTION axon.reject_test_group_route_update()
                    RETURNS trigger AS $$
                    BEGIN
                      IF NEW.group_id = '%s' THEN
                        RAISE EXCEPTION 'forced application group route update failure';
                      END IF;
                      RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """
              .formatted(groupId));
      jdbcTemplate.execute(
          """
                    CREATE TRIGGER reject_test_group_route_update
                    BEFORE UPDATE ON axon.application_group_route
                    FOR EACH ROW EXECUTE FUNCTION axon.reject_test_group_route_update()
                    """);
      assertThatThrownBy(() -> commandGateway.sendAndWait(command))
          .satisfies(
              failure ->
                  assertThat(rootCause(failure))
                      .hasMessageContaining("forced application group route update failure"));
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER IF EXISTS reject_test_group_route_update ON axon.application_group_route");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_group_route_update()");
    }

    assertThat(countDomainEvents(groupId)).isZero();
    assertThat(groupReadRepository.findById(groupId)).isEmpty();
    assertStandaloneRoute(leadApplicationId);
    assertStandaloneRoute(memberApplicationId);
  }

  @Test
  void givenMissingRoutes_whenLinkedGroupCommandIsDispatched_thenEventAppendRollsBack() {
    UUID groupId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    UUID memberApplicationId = UUID.randomUUID();
    var memberApplicationIds = List.of(leadApplicationId, memberApplicationId);

    var command =
        new EstablishLinkedApplicationGroupCommand(
            groupId, leadApplicationId, memberApplicationIds, Instant.now());

    assertThatThrownBy(() -> commandGateway.sendAndWait(command))
        .satisfies(
            failure -> {
              Throwable rootCause = rootCause(failure);
              assertThat(rootCause).isInstanceOf(ResourceNotFoundException.class);
              assertThat(rootCause)
                  .hasMessageContaining("No application group routes found for applications");
            });

    assertThat(countDomainEvents(groupId)).isZero();
    assertThat(groupReadRepository.findById(groupId)).isEmpty();
  }

  private long countDomainEvents(String payloadType) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM axon.domain_event_entry WHERE payload_type = ?",
            Long.class,
            payloadType));
  }

  private long countDomainEvents(UUID aggregateId, String payloadType) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            """
                                SELECT COUNT(*)
                                FROM axon.domain_event_entry
                                WHERE aggregate_identifier = ?
                                  AND payload_type = ?
                                """,
            Long.class,
            aggregateId.toString(),
            payloadType));
  }

  private long countDomainEvents(UUID aggregateId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
            Long.class,
            aggregateId.toString()));
  }

  private long countGroupEvents() {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            """
                                SELECT COUNT(*)
                                FROM axon.domain_event_entry
                                WHERE payload_type IN (?, ?)
                                """,
            Long.class,
            LinkedApplicationGroupCreatedEvent.class.getName(),
            MemberAddedToGroupEvent.class.getName()));
  }

  private long countRoutes(UUID applicationId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM axon.application_group_route WHERE application_id = ?",
            Long.class,
            applicationId));
  }

  private ResponseEntity<Void> linkApplication(UUID sourceId, UUID targetId) {
    ApplicationLinkRequest request =
        new ApplicationLinkRequest(targetId, ApplicationLinkType.FAMILY);
    return restTemplate.exchange(
        "/api/v0/applications/" + sourceId + "/link",
        HttpMethod.POST,
        new HttpEntity<>(request, headers()),
        Void.class);
  }

  private ResponseEntity<String> linkApplicationWithRawJson(UUID sourceId, String requestBody) {
    HttpHeaders rawJsonHeaders = headers();
    rawJsonHeaders.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate.exchange(
        "/api/v0/applications/" + sourceId + "/link",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, rawJsonHeaders),
        String.class);
  }

  private CompletableFuture<ResponseEntity<Void>> concurrentLink(
      ExecutorService executor, CyclicBarrier barrier, UUID sourceId, UUID targetId) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            barrier.await(10, TimeUnit.SECONDS);
            return linkApplication(sourceId, targetId);
          } catch (Exception exception) {
            throw new RuntimeException(exception);
          }
        },
        executor);
  }

  private Throwable rootCause(Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private String uniqueClientLastName() {
    return "Link" + UUID.randomUUID().toString().replace("-", "");
  }

  private void createApplication(UUID applicationId, String clientLastName) {
    createApplication(applicationId, clientLastName, "1A001B");
  }

  private void createApplication(UUID applicationId, String clientLastName, String officeCode) {
    var request = createApplicationRequest(applicationId, clientLastName);
    var applicationContent = new HashMap<>(request.getApplicationContent());
    var originalProvider = (Map<?, ?>) applicationContent.get("provider");
    var provider = new HashMap<String, Object>();
    originalProvider.forEach((key, value) -> provider.put(key.toString(), value));
    provider.put("officeCode", officeCode);
    applicationContent.put("provider", provider);
    request.setApplicationContent(applicationContent);

    var response = post(request, headers());

    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();

    projectionAwaiter.awaitApplication(applicationId);
    awaitRoute(applicationId, ApplicationGroupRouteKind.STANDALONE);
  }

  private Map<UUID, ApplicationGroupRoute> awaitRoutes(UUID... applicationIds) {
    List<UUID> expectedApplicationIds = List.of(applicationIds);
    return await()
        .alias("application group routes for " + expectedApplicationIds)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> routesByApplicationId(expectedApplicationIds),
            routes -> routes.keySet().containsAll(expectedApplicationIds));
  }

  private Map<UUID, ApplicationGroupRoute> routesByApplicationId(List<UUID> applicationIds) {
    return applicationGroupRouteRepository.findAllById(applicationIds).stream()
        .collect(Collectors.toMap(ApplicationGroupRoute::getApplicationId, Function.identity()));
  }

  private ApplicationGroupRoute awaitRoute(
      UUID applicationId, ApplicationGroupRouteKind routeKind) {
    return await()
        .alias("application group route " + routeKind + " for " + applicationId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> applicationGroupRouteRepository.findById(applicationId).orElse(null),
            route -> route != null && route.getRouteKind() == routeKind);
  }

  private void assertStandaloneRoute(UUID applicationId) {
    ApplicationGroupRoute route = awaitRoute(applicationId, ApplicationGroupRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
  }

  private void assertRoutesInGroup(UUID groupId, UUID... applicationIds) {
    Map<UUID, ApplicationGroupRoute> routes = awaitRoutes(applicationIds);
    assertThat(routes.keySet()).containsAll(List.of(applicationIds));
    routes
        .values()
        .forEach(
            route -> {
              assertThat(route.getRouteKind()).isEqualTo(ApplicationGroupRouteKind.LINKED_GROUP);
              assertThat(route.getGroupId()).isEqualTo(groupId);
            });
  }

  private LinkedApplicationGroupReadModel awaitGroupProjection(
      UUID groupId, UUID leadApplicationId, UUID... memberApplicationIds) {
    List<UUID> expectedMemberIds = List.of(memberApplicationIds);
    return await()
        .alias("linked application group projection for " + groupId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> groupReadRepository.findById(groupId).orElse(null),
            group ->
                group != null
                    && leadApplicationId.equals(group.getLeadApplicationId())
                    && group.getMemberIds().size() == expectedMemberIds.size()
                    && new HashSet<>(group.getMemberIds()).equals(Set.copyOf(expectedMemberIds)));
  }

  private boolean summaryGroupReady(
      Map<UUID, ApplicationSummary> summaries,
      UUID leadApplicationId,
      List<UUID> expectedApplicationIds) {
    if (!summaries.keySet().containsAll(expectedApplicationIds)) {
      return false;
    }
    return expectedApplicationIds.stream()
        .allMatch(
            applicationId -> {
              ApplicationSummary summary = summaries.get(applicationId);
              if (!Objects.equals(summary.getIsLead(), applicationId.equals(leadApplicationId))) {
                return false;
              }
              List<LinkedApplicationSummaryResponse> linkedApplications =
                  summary.getLinkedApplications() == null
                      ? List.of()
                      : summary.getLinkedApplications();
              Set<UUID> expectedLinkedApplicationIds =
                  expectedApplicationIds.stream()
                      .filter(expectedApplicationId -> !expectedApplicationId.equals(applicationId))
                      .collect(Collectors.toSet());
              Set<UUID> actualLinkedApplicationIds =
                  linkedApplications.stream()
                      .map(LinkedApplicationSummaryResponse::getApplicationId)
                      .collect(Collectors.toSet());
              return linkedApplications.size() == expectedLinkedApplicationIds.size()
                  && actualLinkedApplicationIds.equals(expectedLinkedApplicationIds)
                  && linkedApplications.stream()
                      .allMatch(
                          linkedApplication ->
                              Objects.equals(
                                  linkedApplication.getIsLead(),
                                  Objects.equals(
                                      linkedApplication.getApplicationId(), leadApplicationId)));
            });
  }

  private void assertApplicationSummaryGroup(
      Map<UUID, ApplicationSummary> summaries,
      UUID leadApplicationId,
      UUID... memberApplicationIds) {
    List<UUID> expectedApplicationIds =
        Stream.concat(Stream.of(leadApplicationId), Stream.of(memberApplicationIds)).toList();
    assertThat(summaries.keySet()).containsAll(expectedApplicationIds);
    expectedApplicationIds.forEach(
        applicationId -> {
          ApplicationSummary summary = summaries.get(applicationId);
          assertThat(summary.getIsLead()).isEqualTo(applicationId.equals(leadApplicationId));
          List<UUID> expectedLinkedApplicationIds =
              expectedApplicationIds.stream()
                  .filter(expectedApplicationId -> !expectedApplicationId.equals(applicationId))
                  .toList();
          assertThat(summary.getLinkedApplications())
              .extracting(LinkedApplicationSummaryResponse::getApplicationId)
              .containsExactlyInAnyOrderElementsOf(expectedLinkedApplicationIds);
          assertThat(summary.getLinkedApplications())
              .allSatisfy(
                  linkedApplication ->
                      assertThat(linkedApplication.getIsLead())
                          .isEqualTo(
                              Objects.equals(
                                  linkedApplication.getApplicationId(), leadApplicationId)));
        });
  }

  private Map<UUID, ApplicationSummary> applicationSummaries(
      String clientLastName, List<UUID> expectedApplicationIds) {
    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications?clientLastName="
                + clientLastName
                + "&pageSize=100",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    if (response.getStatusCode() != HttpStatus.OK) {
      return Map.of();
    }
    ApplicationSummaryResponse body =
        objectMapper.readValue(response.getBody(), ApplicationSummaryResponse.class);
    if (body.getApplications() == null) {
      return Map.of();
    }
    return body.getApplications().stream()
        .filter(summary -> expectedApplicationIds.contains(summary.getApplicationId()))
        .collect(Collectors.toMap(ApplicationSummary::getApplicationId, Function.identity()));
  }

  private void assertGroupMembers(
      LinkedApplicationGroupReadModel group, UUID leadApplicationId, UUID... memberApplicationIds) {
    assertThat(group.getLeadApplicationId()).isEqualTo(leadApplicationId);
    assertThat(group.getMemberIds()).containsExactlyInAnyOrder(memberApplicationIds);
    assertThat(new HashSet<>(group.getMemberIds())).hasSameSizeAs(group.getMemberIds());
  }

  private Map<UUID, ApplicationSummary> awaitApplicationSummaryGroup(
      String clientLastName, UUID leadApplicationId, UUID... memberApplicationIds) {
    List<UUID> expectedApplicationIds =
        Stream.concat(Stream.of(leadApplicationId), Stream.of(memberApplicationIds)).toList();
    return await()
        .alias("application summaries to show linked group for " + expectedApplicationIds)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> applicationSummaries(clientLastName, expectedApplicationIds),
            summaries -> summaryGroupReady(summaries, leadApplicationId, expectedApplicationIds));
  }

  private ApplicationCreateRequest createApplicationRequest(
      UUID applicationId, String clientLastName) {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    Map<String, Object> applicationContent = new HashMap<>(request.getApplicationContent());
    Map<?, ?> originalClient = (Map<?, ?>) applicationContent.get("client");
    Map<String, Object> client = new HashMap<>();
    originalClient.forEach((key, value) -> client.put(key.toString(), value));
    client.put("lastName", clientLastName);
    applicationContent.put("client", client);
    request.setApplicationContent(applicationContent);
    return request;
  }

  private ResponseEntity<Void> post(ApplicationCreateRequest request, HttpHeaders headers) {
    return restTemplate.postForEntity(
        "http://localhost:" + port + "/api/v0/applications",
        new HttpEntity<>(request, headers),
        Void.class);
  }

  private HttpHeaders headers() {
    return headers(1);
  }

  private HttpHeaders headers(int schemaVersion) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", String.valueOf(schemaVersion));
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
