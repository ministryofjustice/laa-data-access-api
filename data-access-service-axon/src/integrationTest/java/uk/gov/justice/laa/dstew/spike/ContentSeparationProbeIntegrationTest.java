package uk.gov.justice.laa.dstew.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.EventCriterion;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventstreaming.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/**
 * Gate 4a probe: observes where a content-separating storage-engine decorator can hook into Axon,
 * using the real Application aggregate. Not production code.
 */
@Testcontainers
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ContentSeparationProbeIntegrationTest.Config.class, TestJwtDecoderConfig.class})
@Sql(
    statements = {
      "CREATE SCHEMA IF NOT EXISTS spike",
      "CREATE TABLE IF NOT EXISTS spike.probe_content ("
          + "event_id text PRIMARY KEY, application_tag text, payload_type text)"
    },
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContentSeparationProbeIntegrationTest {

  private static final String TAG_KEY = "ApplicationAggregate";

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private CommandGateway commandGateway;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Observations observations;

  @AfterEach
  void reset() {
    observations.reset();
  }

  @Test
  void givenDraftStarted_whenAppended_thenDecoratorSeesPayloadTagsAndTransaction() {
    UUID applicationId = UUID.randomUUID();

    startDraft(applicationId);

    observations.print("append");
    assertThat(observations.appendedPayloadClasses).isNotEmpty();
    assertThat(observations.appendedTags)
        .anySatisfy(tags -> assertThat(tags).contains(TAG_KEY + "=" + applicationId));
    assertThat(contentRows(applicationId)).isEqualTo(observations.appendedPayloadClasses.size());
  }

  @Test
  void givenExistingDraft_whenAggregateLoaded_thenCriteriaNameTheApplication() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    startDraft(applicationId, request);
    observations.reset();

    // An idempotent retry of the same request loads the aggregate without appending.
    startDraft(applicationId, request);

    observations.print("source");
    assertThat(observations.sourcedTags)
        .anySatisfy(tags -> assertThat(tags).contains(TAG_KEY + "=" + applicationId));
    assertThat(observations.sourcedPayloadClasses).isNotEmpty();
    assertThat(observations.appendedPayloadClasses).isEmpty();
  }

  @Test
  void givenAppendCommitFails_whenDraftStarted_thenWritesWereAlreadyCommitted() {
    UUID applicationId = UUID.randomUUID();
    observations.failMode = FailMode.APPEND_TRANSACTION_COMMIT;

    assertThatThrownBy(() -> startDraft(applicationId)).isNotNull();

    observations.print("commit-failure");
    // The append transaction's commit runs after the database transaction has committed.
    assertThat(contentRows(applicationId)).isEqualTo(1);
    assertThat(domainEvents(applicationId)).isEqualTo(1);
  }

  @Test
  void givenFailureAfterBothWrites_whenDraftStarted_thenContentAndEventRollBackTogether() {
    UUID applicationId = UUID.randomUUID();
    observations.failMode = FailMode.AFTER_DELEGATE_APPEND;

    assertThatThrownBy(() -> startDraft(applicationId)).isNotNull();

    observations.print("append-failure");
    assertThat(observations.appendedPayloadClasses).isNotEmpty();
    assertThat(contentRows(applicationId)).isZero();
    assertThat(domainEvents(applicationId)).isZero();
  }

  private void startDraft(UUID applicationId) {
    startDraft(applicationId, validCreateApplicationRequest(applicationId, UUID.randomUUID()));
  }

  private void startDraft(UUID applicationId, ApplicationCreateRequest request) {
    commandGateway.sendAndWait(
        new CreateApplicationDraftCommand(
            applicationId,
            "APPLICATION_SUBMITTED",
            request.getLaaReference(),
            request.getApplicationContent(),
            objectMapper.writeValueAsString(request),
            1,
            "BaseCivilApplication.json",
            Instant.now(),
            request.getPotentialDuplicates()));
  }

  private int contentRows(UUID applicationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM spike.probe_content WHERE application_tag = ?",
        Integer.class,
        applicationId.toString());
  }

  private int domainEvents(UUID applicationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
        Integer.class,
        applicationId.toString());
  }

  @TestConfiguration
  static class Config {

    @Bean
    Observations probeObservations() {
      return new Observations();
    }

    @Bean
    ConfigurationEnhancer probeStorageEngineDecorator(
        Observations observations, ObjectProvider<JdbcTemplate> jdbc) {
      return registry ->
          registry.registerDecorator(
              EventStorageEngine.class,
              0,
              (config, name, delegate) -> new ProbeEngine(delegate, jdbc, observations));
    }
  }

  enum FailMode {
    NONE,
    APPEND_TRANSACTION_COMMIT,
    AFTER_DELEGATE_APPEND
  }

  static final class Observations {
    final List<String> appendedPayloadClasses = new CopyOnWriteArrayList<>();
    final List<Set<String>> appendedTags = new CopyOnWriteArrayList<>();
    final List<String> transactionAtAppend = new CopyOnWriteArrayList<>();
    final List<String> transactionAtCommit = new CopyOnWriteArrayList<>();
    final List<Set<String>> sourcedTags = new CopyOnWriteArrayList<>();
    final List<String> sourcedPayloadClasses = new CopyOnWriteArrayList<>();
    volatile FailMode failMode = FailMode.NONE;

    void reset() {
      appendedPayloadClasses.clear();
      appendedTags.clear();
      transactionAtAppend.clear();
      transactionAtCommit.clear();
      sourcedTags.clear();
      sourcedPayloadClasses.clear();
      failMode = FailMode.NONE;
    }

    void print(String label) {
      System.out.printf(
          "PROBE %s appendedPayloadClasses=%s appendedTags=%s transactionAtAppend=%s"
              + " transactionAtCommit=%s sourcedTags=%s sourcedPayloadClasses=%s%n",
          label,
          appendedPayloadClasses,
          appendedTags,
          transactionAtAppend,
          transactionAtCommit,
          sourcedTags,
          sourcedPayloadClasses);
    }

    static String transaction() {
      return TransactionSynchronizationManager.isActualTransactionActive()
          + ":"
          + TransactionSynchronizationManager.getCurrentTransactionName()
          + ":thread="
          + Thread.currentThread().getName();
    }
  }

  static final class ProbeFailure extends RuntimeException {
    ProbeFailure() {
      super("Probe forced append commit failure");
    }
  }

  static final class ProbeEngine implements EventStorageEngine {
    private final EventStorageEngine delegate;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final Observations observations;

    ProbeEngine(
        EventStorageEngine delegate, ObjectProvider<JdbcTemplate> jdbc, Observations observations) {
      this.delegate = delegate;
      this.jdbc = jdbc;
      this.observations = observations;
    }

    @Override
    public CompletableFuture<AppendTransaction<?>> appendEvents(
        AppendCondition condition, ProcessingContext context, List<TaggedEventMessage<?>> events) {
      observations.transactionAtAppend.add(Observations.transaction());
      for (TaggedEventMessage<?> tagged : events) {
        EventMessage event = tagged.event();
        observations.appendedPayloadClasses.add(
            event.payload() == null ? "<no payload>" : event.payload().getClass().getName());
        observations.appendedTags.add(tagStrings(tagged.tags()));
        jdbc.getObject()
            .update(
                "INSERT INTO spike.probe_content (event_id, application_tag, payload_type)"
                    + " VALUES (?, ?, ?)",
                event.identifier(),
                tagValue(tagged.tags()),
                event.type().name());
      }
      CompletableFuture<AppendTransaction<?>> appended =
          delegate.appendEvents(condition, context, events).thenApply(this::wrap);
      if (observations.failMode == FailMode.AFTER_DELEGATE_APPEND) {
        return appended.thenCompose(
            transaction -> CompletableFuture.failedFuture(new ProbeFailure()));
      }
      return appended;
    }

    private <R> AppendTransaction<R> wrap(AppendTransaction<R> transaction) {
      return new AppendTransaction<>() {
        @Override
        public CompletableFuture<R> commit() {
          observations.transactionAtCommit.add(Observations.transaction());
          if (observations.failMode == FailMode.APPEND_TRANSACTION_COMMIT) {
            return CompletableFuture.failedFuture(new ProbeFailure());
          }
          return transaction.commit();
        }

        @Override
        public void rollback() {
          transaction.rollback();
        }

        @Override
        public CompletableFuture<ConsistencyMarker> afterCommit(R result) {
          return transaction.afterCommit(result);
        }
      };
    }

    @Override
    public MessageStream<EventMessage> source(
        SourcingCondition condition, ProcessingContext context) {
      observations.sourcedTags.add(
          condition.criteria().flatten().stream()
              .map(EventCriterion::tags)
              .flatMap(Set::stream)
              .map(tag -> tag.key() + "=" + tag.value())
              .collect(Collectors.toSet()));
      return delegate
          .source(condition, context)
          .onNext(
              entry ->
                  observations.sourcedPayloadClasses.add(
                      entry.message() == null || entry.message().payload() == null
                          ? "<no payload>"
                          : entry.message().payload().getClass().getName()));
    }

    @Override
    public MessageStream<EventMessage> stream(StreamingCondition condition) {
      return delegate.stream(condition);
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken() {
      return delegate.firstToken();
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken() {
      return delegate.latestToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(Instant at) {
      return delegate.tokenAt(at);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
      descriptor.describeWrapperOf(delegate);
    }

    private static Set<String> tagStrings(Set<Tag> tags) {
      return tags.stream().map(tag -> tag.key() + "=" + tag.value()).collect(Collectors.toSet());
    }

    private static String tagValue(Set<Tag> tags) {
      return tags.stream()
          .filter(tag -> TAG_KEY.equals(tag.key()))
          .map(Tag::value)
          .findFirst()
          .orElse(null);
    }
  }
}
