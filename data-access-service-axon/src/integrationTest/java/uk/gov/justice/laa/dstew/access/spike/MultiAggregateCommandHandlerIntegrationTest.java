package uk.gov.justice.laa.dstew.access.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.access.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListUnassignRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

class MultiAggregateCommandHandlerIntegrationTest extends BaseIntegrationTest {

  @Autowired private CommandGateway commandGateway;

  @Autowired private RetryingCommandDispatcher retryingDispatcher;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private ApplicationGroupRouteRepository routeRepository;

  @AfterEach
  void resetSpikeStateAndDropTrigger() {
    AppendToTwoApplicationsCommandHandler.BEFORE_APPEND.set(() -> {});
    AppendToTwoApplicationsCommandHandler.SAME_INSTANCE_WHEN_RELOADED.set(false);
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS reject_test_group_assignment_event ON axon.domain_event_entry");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS axon.reject_test_group_assignment_event()");
  }

  @Test
  void givenTwoApplications_whenHandlerAppendsToBoth_thenBothStreamsAreUpdated() {
    var firstApplicationId = createApplication(uniqueLastName());
    var secondApplicationId = createApplication(uniqueLastName());
    var occurredAt = Instant.now();

    commandGateway.sendAndWait(
        command(
            firstApplicationId,
            secondApplicationId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            occurredAt));

    assertThat(countDomainEvents(firstApplicationId, WorkItemAssigned.class)).isEqualTo(1);
    assertThat(countDomainEvents(secondApplicationId, WorkItemAssigned.class)).isEqualTo(1);
    assertSequenceHasNoGaps(firstApplicationId);
    assertSequenceHasNoGaps(secondApplicationId);
    awaitWorkListAssignee(firstApplicationId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    awaitWorkListAssignee(secondApplicationId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    assertThat(AppendToTwoApplicationsCommandHandler.SAME_INSTANCE_WHEN_RELOADED.get()).isTrue();

    var response =
        restTemplate.exchange(
            url("/api/v0/work-list/" + secondApplicationId + "/unassign"),
            HttpMethod.POST,
            new HttpEntity<>(
                new WorkListUnassignRequest(1L), headersFor(TestJwtDecoderConfig.CASEWORKER_ID)),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    awaitWorkListAssignee(secondApplicationId, null, 2L);
  }

  @Test
  void givenSecondStreamRejectsInsert_whenHandlerAppends_thenNeitherStreamChanges() {
    var firstApplicationId = createApplication(uniqueLastName());
    var secondApplicationId = createApplication(uniqueLastName());
    rejectEventsFor(secondApplicationId);

    assertThatThrownBy(
            () ->
                commandGateway.sendAndWait(
                    command(
                        firstApplicationId,
                        secondApplicationId,
                        TestJwtDecoderConfig.CASEWORKER_ID,
                        Instant.now())))
        .hasStackTraceContaining("forced group assignment failure");

    assertThat(countDomainEvents(firstApplicationId, WorkItemAssigned.class)).isZero();
    assertThat(countDomainEvents(secondApplicationId, WorkItemAssigned.class)).isZero();
  }

  @Test
  void givenConcurrentWriteToLoadedApplication_whenHandlerAppends_thenAppendIsRejected() {
    var firstApplicationId = createApplication(uniqueLastName());
    var secondApplicationId = createApplication(uniqueLastName());
    AppendToTwoApplicationsCommandHandler.BEFORE_APPEND.set(
        () -> {
          var response = assign(secondApplicationId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 0L);
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        });

    assertThatThrownBy(
            () ->
                commandGateway.sendAndWait(
                    command(
                        firstApplicationId,
                        secondApplicationId,
                        TestJwtDecoderConfig.CASEWORKER_ID,
                        Instant.now())))
        .isNotNull();

    assertThat(countDomainEvents(firstApplicationId, WorkItemAssigned.class)).isZero();
    assertThat(countDomainEvents(secondApplicationId, WorkItemAssigned.class)).isEqualTo(1);
    awaitWorkListAssignee(secondApplicationId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 1L);
    assertThat(assignedEvent(secondApplicationId))
        .contains(TestJwtDecoderConfig.OTHER_CASEWORKER_ID.toString());
  }

  @Test
  void givenConcurrentWrite_whenDispatchedThroughRetryingDispatcher_thenOutcomeIsRecorded() {
    var firstApplicationId = createApplication(uniqueLastName());
    var secondApplicationId = createApplication(uniqueLastName());
    var callbackCount = new AtomicInteger();
    var firstAttemptCompleted = new AtomicBoolean();
    AppendToTwoApplicationsCommandHandler.BEFORE_APPEND.set(
        () -> {
          if (callbackCount.getAndIncrement() == 0) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                  @Override
                  public void afterCompletion(int status) {
                    firstAttemptCompleted.set(true);
                  }
                });
            var response =
                assign(secondApplicationId, TestJwtDecoderConfig.OTHER_CASEWORKER_ID, 0L);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
          } else {
            assertThat(firstAttemptCompleted)
                .as("the rejected attempt must complete before its retry")
                .isTrue();
          }
        });
    var command =
        command(
            firstApplicationId,
            secondApplicationId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            Instant.now());
    retryingDispatcher.dispatch(
        () ->
            new TransactionTemplate(transactionManager)
                .execute(status -> commandGateway.sendAndWait(command)));
    awaitWorkListAssignee(firstApplicationId, TestJwtDecoderConfig.CASEWORKER_ID, 1L);
    awaitWorkListAssignee(secondApplicationId, TestJwtDecoderConfig.CASEWORKER_ID, 2L);
  }

  private AppendToTwoApplicationsCommand command(
      UUID firstApplicationId, UUID secondApplicationId, UUID caseworkerId, Instant occurredAt) {
    return new AppendToTwoApplicationsCommand(
        firstApplicationId, secondApplicationId, caseworkerId, occurredAt);
  }

  private UUID createApplication(String lastName) {
    var applicationId = UUID.randomUUID();
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

    var created =
        restTemplate.postForEntity(
            url("/api/v0/applications"), new HttpEntity<>(request, headers()), Void.class);
    assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);

    var ready =
        restTemplate.exchange(
            url("/api/v0/applications/" + applicationId + "/auto-grant-outcome"),
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
            Void.class);
    assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> routeRepository.findById(applicationId).orElse(null),
            route -> route != null && route.getRouteKind() == ApplicationGroupRouteKind.STANDALONE);
    return applicationId;
  }

  private ResponseEntity<String> assign(UUID itemId, UUID caseworkerId, long expectedVersion) {
    return restTemplate.exchange(
        url("/api/v0/work-list/" + itemId + "/assign"),
        HttpMethod.POST,
        new HttpEntity<>(new WorkListAssignRequest(expectedVersion), headersFor(caseworkerId)),
        String.class);
  }

  private long countDomainEvents(UUID aggregateId, Class<?> eventType) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM axon.domain_event_entry
        WHERE aggregate_identifier = ?
          AND payload_type = ?
        """,
        Long.class,
        aggregateId.toString(),
        eventType.getName());
  }

  private String assignedEvent(UUID aggregateId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT convert_from(payload, 'UTF8')
        FROM axon.domain_event_entry
        WHERE aggregate_identifier = ?
          AND payload_type = ?
        """,
        String.class,
        aggregateId.toString(),
        WorkItemAssigned.class.getName());
  }

  private void assertSequenceHasNoGaps(UUID aggregateId) {
    List<Long> sequenceNumbers =
        jdbcTemplate.queryForList(
            """
            SELECT sequence_number
            FROM axon.domain_event_entry
            WHERE aggregate_identifier = ?
            ORDER BY sequence_number
            """,
            Long.class,
            aggregateId.toString());
    assertThat(sequenceNumbers).containsExactly(0L, 1L, 2L);
  }

  private void awaitWorkListAssignee(UUID itemId, UUID expectedAssignee, long expectedVersion) {
    await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              Map<String, Object> row =
                  jdbcTemplate.queryForMap(
                      "SELECT assignee_id, assignment_version FROM axon.work_list_item WHERE item_id = ?",
                      itemId);
              assertThat(row.get("assignee_id")).isEqualTo(expectedAssignee);
              assertThat(((Number) row.get("assignment_version")).longValue())
                  .isEqualTo(expectedVersion);
            });
  }

  private void rejectEventsFor(UUID aggregateId) {
    jdbcTemplate.execute(
        """
        CREATE OR REPLACE FUNCTION axon.reject_test_group_assignment_event()
        RETURNS trigger AS $$
        BEGIN
          IF NEW.aggregate_identifier = '%s' THEN
            RAISE EXCEPTION 'forced group assignment failure';
          END IF;
          RETURN NEW;
        END;
        $$ LANGUAGE plpgsql
        """
            .formatted(aggregateId));
    jdbcTemplate.execute(
        """
        CREATE TRIGGER reject_test_group_assignment_event
        BEFORE INSERT ON axon.domain_event_entry
        FOR EACH ROW EXECUTE FUNCTION axon.reject_test_group_assignment_event()
        """);
  }

  private String uniqueLastName() {
    return "MultiAggregate" + UUID.randomUUID().toString().replace("-", "");
  }
}
