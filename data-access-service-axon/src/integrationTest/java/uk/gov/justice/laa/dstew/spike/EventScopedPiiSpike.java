package uk.gov.justice.laa.dstew.spike;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.extension.spring.config.SpringEventSourcedEntityConfigurer;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Test-only experiment; no production handlers, entities or migrations are replaced. */
final class EventScopedPiiSpike {

  static final String PROCESSOR = "event-scoped-pii-spike";

  private EventScopedPiiSpike() {}

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {

    @Bean
    ConfigurationEnhancer spikeEntity() {
      return new SpringEventSourcedEntityConfigurer<>(SpikeApplication.class, UUID.class);
    }

    @Bean
    Store spikeStore(JdbcTemplate jdbc, ObjectMapper mapper) {
      return new Store(jdbc, mapper);
    }

    @Bean
    WriteProbe spikeWriteProbe() {
      return new WriteProbe();
    }

    @Bean
    Handlers spikeHandlers(Store store, WriteProbe probe) {
      return new Handlers(store, probe);
    }

    @Bean
    Projection spikeProjection(Store store) {
      return new Projection(store);
    }

    @Bean
    EventProcessorDefinition spikeProcessor() {
      return EventProcessorDefinition.pooledStreamingMatching(PROCESSOR)
          .customized(config -> config.errorHandler(PropagatingErrorHandler.instance()));
    }
  }

  @Command(routingKey = "applicationId")
  record StartDraft(UUID applicationId, String officeCode, Content content) {}

  // Different document routes exercise competing appends without the local command sequencer.
  @Command(routingKey = "documentId")
  record Upload(
      UUID applicationId,
      long expectedVersion,
      UUID documentId,
      String documentType,
      String filename) {}

  @Command(routingKey = "applicationId")
  record Rename(UUID applicationId, long expectedVersion, UUID documentId, String filename) {}

  @Command(routingKey = "applicationId")
  record UpdateDraft(UUID applicationId, long expectedVersion, Body body) {}

  @Command(routingKey = "applicationId")
  record PrepareDeclaration(UUID applicationId, long expectedVersion) {}

  @Command(routingKey = "applicationId")
  record Submit(UUID applicationId, long expectedVersion) {}

  // Alternative to PrepareDeclaration: the reviewed version and hash bind submit without a write.
  @Command(routingKey = "applicationId")
  record SubmitReviewed(UUID applicationId, long expectedVersion, String contentHash) {}

  @Command(routingKey = "applicationId")
  record Review(UUID applicationId, long expectedVersion) {}

  @Command(routingKey = "applicationId")
  record CompactDraftContent(UUID applicationId, long expectedVersion) {}

  interface Fact {
    UUID applicationId();

    long version();
  }

  @Event
  record DraftStarted(
      @EventTag(key = "SpikeApplication") UUID applicationId, long version, String officeCode)
      implements Fact {}

  @Event
  record DocumentUploaded(
      @EventTag(key = "SpikeApplication") UUID applicationId,
      long version,
      UUID documentId,
      String documentType)
      implements Fact {}

  @Event
  record FilenameChanged(
      @EventTag(key = "SpikeApplication") UUID applicationId, long version, UUID documentId)
      implements Fact {}

  @Event
  record DraftUpdated(@EventTag(key = "SpikeApplication") UUID applicationId, long version)
      implements Fact {}

  @Event
  record DeclarationPrepared(@EventTag(key = "SpikeApplication") UUID applicationId, long version)
      implements Fact {}

  @Event
  record Submitted(@EventTag(key = "SpikeApplication") UUID applicationId, long version)
      implements Fact {}

  @Event
  record Reviewed(@EventTag(key = "SpikeApplication") UUID applicationId, long version)
      implements Fact {}

  @Event
  record DraftContentCompacted(
      @EventTag(key = "SpikeApplication") UUID applicationId, long version, long submissionVersion)
      implements Fact {}

  record DraftCandidate(UUID applicationId, long streamVersion, String eventId, Content content) {}

  record ReviewedDraft(
      UUID applicationId, long streamVersion, Content content, String contentHash) {}

  record Filename(String fileName) {}

  record Body(String applicantName, String details) {}

  record Content(String applicantName, String details, Map<UUID, String> documentFilenames) {
    Content {
      documentFilenames = Map.copyOf(documentFilenames);
    }

    Content withFilename(UUID id, String filename) {
      var filenames = new HashMap<>(documentFilenames);
      filenames.put(id, filename);
      return new Content(applicantName, details, filenames);
    }

    Content withBody(Body body) {
      return new Content(body.applicantName(), body.details(), documentFilenames);
    }
  }

  record State(
      UUID applicationId,
      long version,
      String officeCode,
      boolean submitted,
      boolean reviewed,
      Long declarationVersion,
      Long submissionVersion,
      boolean draftContentCompacted,
      Map<UUID, String> documentTypes) {

    static State empty() {
      return new State(null, -1, null, false, false, null, null, false, Map.of());
    }

    State apply(Fact fact) {
      if (fact.version() != version + 1) {
        throw new IllegalStateException("Non-contiguous spike stream");
      }
      var documents = new HashMap<>(documentTypes);
      if (fact instanceof DocumentUploaded uploaded) {
        documents.put(uploaded.documentId(), uploaded.documentType());
      }
      return new State(
          fact.applicationId(),
          fact.version(),
          fact instanceof DraftStarted started ? started.officeCode() : officeCode,
          submitted || fact instanceof Submitted,
          reviewed || fact instanceof Reviewed,
          fact instanceof DeclarationPrepared ? Long.valueOf(fact.version()) : declarationVersion,
          fact instanceof Submitted ? Long.valueOf(fact.version()) : submissionVersion,
          draftContentCompacted || fact instanceof DraftContentCompacted,
          Map.copyOf(documents));
    }
  }

  @EventSourced(tagKey = "SpikeApplication", idType = UUID.class)
  static class SpikeApplication {
    private State state = State.empty();

    @EntityCreator
    protected SpikeApplication() {}

    @EventSourcingHandler
    void on(DraftStarted fact) {
      state = state.apply(fact);
    }

    @EventSourcingHandler
    void on(DocumentUploaded fact) {
      state = state.apply(fact);
    }

    @EventSourcingHandler
    void on(FilenameChanged fact) {
      state = state.apply(fact);
    }

    @EventSourcingHandler
    void on(DraftUpdated fact) {
      state = state.apply(fact);
    }

    @EventSourcingHandler
    void on(DeclarationPrepared fact) {
      state = state.apply(fact);
    }

    @EventSourcingHandler
    void on(Submitted fact) {
      state = state.apply(fact);
    }

    @EventSourcingHandler
    void on(Reviewed fact) {
      state = state.apply(fact);
    }

    @EventSourcingHandler
    void on(DraftContentCompacted fact) {
      state = state.apply(fact);
    }
  }

  static class Handlers {
    private final Store store;
    private final WriteProbe probe;

    Handlers(Store store, WriteProbe probe) {
      this.store = store;
      this.probe = probe;
    }

    @CommandHandler
    void start(
        StartDraft command,
        @InjectEntity(idProperty = "applicationId") @Nullable SpikeApplication application,
        EventAppender appender) {
      if (application != null) {
        throw new IllegalStateException("Spike draft already exists");
      }
      validate(command.content());
      append(
          new DraftStarted(command.applicationId(), 0, command.officeCode()),
          command.content(),
          appender);
    }

    @CommandHandler
    void upload(
        Upload command,
        @InjectEntity(idProperty = "applicationId") SpikeApplication application,
        EventAppender appender) {
      State state = requireVersion(application, command.expectedVersion());
      requireDraft(state);
      if (state.documentTypes().containsKey(command.documentId())) {
        throw new IllegalStateException("Spike document already exists");
      }
      requireFilename(command.filename());
      append(
          new DocumentUploaded(
              command.applicationId(),
              state.version() + 1,
              command.documentId(),
              command.documentType()),
          new Filename(command.filename()),
          appender);
    }

    @CommandHandler
    void rename(
        Rename command,
        @InjectEntity(idProperty = "applicationId") SpikeApplication application,
        EventAppender appender) {
      State state = requireVersion(application, command.expectedVersion());
      requireDraft(state);
      if (!state.documentTypes().containsKey(command.documentId())) {
        throw new IllegalStateException("Spike document does not exist");
      }
      requireFilename(command.filename());
      append(
          new FilenameChanged(command.applicationId(), state.version() + 1, command.documentId()),
          new Filename(command.filename()),
          appender);
    }

    @CommandHandler
    void updateDraft(
        UpdateDraft command,
        @InjectEntity(idProperty = "applicationId") SpikeApplication application,
        EventAppender appender) {
      State state = requireVersion(application, command.expectedVersion());
      requireDraft(state);
      validate(new Content(command.body().applicantName(), command.body().details(), Map.of()));
      append(
          new DraftUpdated(command.applicationId(), state.version() + 1), command.body(), appender);
    }

    @CommandHandler
    long prepareDeclaration(
        PrepareDeclaration command,
        @InjectEntity(idProperty = "applicationId") SpikeApplication application,
        EventAppender appender) {
      State state = requireVersion(application, command.expectedVersion());
      requireDraft(state);
      Content content = store.reconstruct(command.applicationId(), state.version()).content();
      validate(content);
      long version = state.version() + 1;
      append(new DeclarationPrepared(command.applicationId(), version), content, appender);
      return version;
    }

    @CommandHandler
    void submit(
        Submit command,
        @InjectEntity(idProperty = "applicationId") SpikeApplication application,
        EventAppender appender) {
      State state = requireVersion(application, command.expectedVersion());
      requireDraft(state);
      if (!Long.valueOf(state.version()).equals(state.declarationVersion())) {
        throw new DeclarationRequired();
      }
      DraftCandidate candidate = store.candidateAt(command.applicationId(), state.version());
      validate(candidate.content());
      Fact fact = new Submitted(command.applicationId(), state.version() + 1);
      var message = new GenericEventMessage(new MessageType(fact.getClass()), fact);
      store.copyContent(candidate.eventId(), message.identifier());
      publish(fact, message, appender);
    }

    @CommandHandler
    void submitReviewed(
        SubmitReviewed command,
        @InjectEntity(idProperty = "applicationId") SpikeApplication application,
        EventAppender appender) {
      State state = requireVersion(application, command.expectedVersion());
      requireDraft(state);
      Content content = store.reconstruct(command.applicationId(), state.version()).content();
      if (!store.contentHash(content).equals(command.contentHash())) {
        throw new ReviewedContentChanged();
      }
      validate(content);
      append(new Submitted(command.applicationId(), state.version() + 1), content, appender);
    }

    @CommandHandler
    void review(
        Review command,
        @InjectEntity(idProperty = "applicationId") SpikeApplication application,
        EventAppender appender) {
      State state = requireVersion(application, command.expectedVersion());
      if (!state.submitted()) {
        throw new IllegalStateException("Spike application is not submitted");
      }
      append(new Reviewed(command.applicationId(), state.version() + 1), null, appender);
    }

    @CommandHandler
    void compact(
        CompactDraftContent command,
        @InjectEntity(idProperty = "applicationId") SpikeApplication application,
        EventAppender appender) {
      State state = requireVersion(application, command.expectedVersion());
      if (!state.submitted() || state.submissionVersion() == null) {
        throw new IllegalStateException("Spike application is not submitted");
      }
      if (state.draftContentCompacted()) {
        return;
      }
      store.reconstruct(command.applicationId(), state.submissionVersion());
      store.deleteDraftContent(command.applicationId(), state.submissionVersion());
      append(
          new DraftContentCompacted(
              command.applicationId(), state.version() + 1, state.submissionVersion()),
          null,
          appender);
    }

    private void append(Fact fact, @Nullable Object content, EventAppender appender) {
      var message = new GenericEventMessage(new MessageType(fact.getClass()), fact);
      if (content != null) {
        store.insertContent(message.identifier(), content);
      }
      publish(fact, message, appender);
    }

    private void publish(Fact fact, GenericEventMessage message, EventAppender appender) {
      probe.beforeAppend(fact);
      appender.append(message);
      probe.afterAppend(fact.applicationId());
    }

    private static State requireVersion(SpikeApplication application, long expected) {
      if (application.state.version() != expected) {
        throw new StaleStreamVersion();
      }
      return application.state;
    }

    private static void requireDraft(State state) {
      if (state.submitted()) {
        throw new IllegalStateException("Spike application is already submitted");
      }
    }

    private static void requireFilename(String filename) {
      if (filename == null || filename.isBlank()) {
        throw new IllegalArgumentException("Spike filename is required");
      }
    }

    private static void validate(Content content) {
      if (content.applicantName() == null || content.applicantName().isBlank()) {
        throw new IllegalArgumentException("Spike applicant name is required");
      }
    }
  }

  record View(State state, Content content) {}

  record StoredFact(String eventId, long sequence, Fact fact) {}

  record StoredContent(@Nullable Integer schemaVersion, @Nullable String payload, boolean erased) {}

  record HistoryEntry(StoredFact stored, StoredContent content) {}

  static class Store {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    Store(JdbcTemplate jdbc, ObjectMapper mapper) {
      this.jdbc = jdbc;
      this.mapper = mapper;
    }

    void insertContent(String eventId, Object content) {
      jdbc.update(
          "INSERT INTO spike.event_content (event_id, schema_version, payload) "
              + "VALUES (?, 1, CAST(? AS jsonb))",
          eventId,
          mapper.writeValueAsString(content));
    }

    void copyContent(String sourceEventId, String targetEventId) {
      int inserted =
          jdbc.update(
              "INSERT INTO spike.event_content (event_id, schema_version, payload) "
                  + "SELECT ?, schema_version, payload FROM spike.event_content "
                  + "WHERE event_id = ? AND payload IS NOT NULL AND erased_at IS NULL",
              targetEventId,
              sourceEventId);
      if (inserted != 1) {
        throw new IllegalStateException("Declaration content unavailable: " + sourceEventId);
      }
    }

    <T> T eventContent(String eventId, Class<T> type) {
      var rows =
          jdbc.query(
              "SELECT schema_version, payload::text AS content_payload, erased_at "
                  + "FROM spike.event_content WHERE event_id = ?",
              (row, index) ->
                  new StoredContent(
                      row.getObject("schema_version", Integer.class),
                      row.getString("content_payload"),
                      row.getTimestamp("erased_at") != null),
              eventId);
      if (rows.isEmpty()) {
        throw new IllegalStateException("Required spike event content missing: " + eventId);
      }
      return decodeContent(eventId, rows.getFirst(), type);
    }

    private <T> T decodeContent(String eventId, StoredContent content, Class<T> type) {
      if (content.schemaVersion() == null) {
        throw new IllegalStateException("Required spike event content missing: " + eventId);
      }
      if (content.erased()) {
        throw new IllegalStateException("Spike event content intentionally erased: " + eventId);
      }
      if (!Integer.valueOf(1).equals(content.schemaVersion())) {
        throw new IllegalStateException("Unsupported spike event content schema: " + eventId);
      }
      return mapper.readValue(content.payload(), type);
    }

    List<StoredFact> facts(UUID applicationId, long throughVersion) {
      return jdbc.query(
          "SELECT event_identifier, sequence_number, payload_type, payload "
              + "FROM axon.domain_event_entry WHERE aggregate_identifier = ? "
              + "AND sequence_number <= ? ORDER BY sequence_number",
          (row, index) ->
              new StoredFact(
                  row.getString("event_identifier"),
                  row.getLong("sequence_number"),
                  decode(row.getString("payload_type"), row.getBytes("payload"))),
          applicationId.toString(),
          throughVersion);
    }

    State domainState(UUID applicationId, long throughVersion) {
      State state = State.empty();
      for (StoredFact stored : facts(applicationId, throughVersion)) {
        state = state.apply(stored.fact());
      }
      return state;
    }

    View reconstructForProjection(UUID applicationId, long processedVersion) {
      List<HistoryEntry> history = history(applicationId);
      long targetVersion = Math.max(processedVersion, compactionCheckpoint(history));
      return reconstruct(history, targetVersion);
    }

    DraftCandidate getDraft(UUID applicationId) {
      List<HistoryEntry> history = history(applicationId);
      State state = State.empty();
      for (HistoryEntry entry : history) {
        state = state.apply(entry.stored().fact());
      }
      if (state.submitted()) {
        throw new IllegalStateException("Spike application is already submitted");
      }
      if (history.isEmpty() || !Long.valueOf(state.version()).equals(state.declarationVersion())) {
        throw new DeclarationRequired();
      }
      return candidate(history.getLast());
    }

    DraftCandidate candidateAt(UUID applicationId, long version) {
      List<HistoryEntry> history = history(applicationId);
      return history.stream()
          .filter(entry -> entry.stored().sequence() == version)
          .findFirst()
          .map(this::candidate)
          .orElseThrow(DeclarationRequired::new);
    }

    ReviewedDraft review(UUID applicationId) {
      List<HistoryEntry> history = history(applicationId);
      if (history.isEmpty()) {
        throw new IllegalStateException("Spike draft does not exist");
      }
      View view = reconstruct(history, history.getLast().stored().sequence());
      if (view.state().submitted()) {
        throw new IllegalStateException("Spike application is already submitted");
      }
      return new ReviewedDraft(
          applicationId, view.state().version(), view.content(), contentHash(view.content()));
    }

    String contentHash(Content content) {
      var filenames = new TreeMap<String, String>();
      content.documentFilenames().forEach((id, name) -> filenames.put(id.toString(), name));
      var canonical = new LinkedHashMap<String, Object>();
      canonical.put("schemaVersion", 1);
      canonical.put("applicantName", content.applicantName());
      canonical.put("details", content.details());
      canonical.put("documentFilenames", filenames);
      try {
        return HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical)));
      } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException("SHA-256 unavailable", exception);
      }
    }

    private DraftCandidate candidate(HistoryEntry entry) {
      if (!(entry.stored().fact() instanceof DeclarationPrepared prepared)) {
        throw new DeclarationRequired();
      }
      return new DraftCandidate(
          prepared.applicationId(),
          prepared.version(),
          entry.stored().eventId(),
          decodeContent(entry.stored().eventId(), entry.content(), Content.class));
    }

    private List<HistoryEntry> history(UUID applicationId) {
      // One statement keeps compaction markers and content in the same PostgreSQL read snapshot.
      return jdbc.query(
          "SELECT e.event_identifier, e.sequence_number, e.payload_type, e.payload, "
              + "c.schema_version, c.payload::text AS content_payload, c.erased_at "
              + "FROM axon.domain_event_entry e "
              + "LEFT JOIN spike.event_content c ON c.event_id = e.event_identifier "
              + "WHERE e.aggregate_identifier = ? ORDER BY e.sequence_number",
          (row, index) ->
              new HistoryEntry(
                  new StoredFact(
                      row.getString("event_identifier"),
                      row.getLong("sequence_number"),
                      decode(row.getString("payload_type"), row.getBytes("payload"))),
                  new StoredContent(
                      row.getObject("schema_version", Integer.class),
                      row.getString("content_payload"),
                      row.getTimestamp("erased_at") != null)),
          applicationId.toString());
    }

    private long compactionCheckpoint(List<HistoryEntry> history) {
      return history.stream()
          .map(entry -> entry.stored().fact())
          .filter(DraftContentCompacted.class::isInstance)
          .map(DraftContentCompacted.class::cast)
          .mapToLong(DraftContentCompacted::submissionVersion)
          .max()
          .orElse(-1);
    }

    View reconstruct(UUID applicationId, long throughVersion) {
      return reconstruct(history(applicationId), throughVersion);
    }

    private View reconstruct(List<HistoryEntry> history, long throughVersion) {
      if (throughVersion < compactionCheckpoint(history)) {
        throw new DraftHistoryCompacted();
      }
      State state = State.empty();
      HistoryEntry checkpoint = null;
      for (HistoryEntry entry : history) {
        StoredFact stored = entry.stored();
        if (stored.sequence() > throughVersion) {
          break;
        }
        Fact fact = stored.fact();
        if (stored.sequence() != fact.version()) {
          throw new IllegalStateException("Spike version differs from stored stream position");
        }
        state = state.apply(fact);
        if (fact instanceof DraftStarted
            || fact instanceof DeclarationPrepared
            || fact instanceof Submitted) {
          checkpoint = entry;
        }
      }
      if (state.version() != throughVersion || checkpoint == null) {
        throw new IllegalStateException("Requested spike stream position unavailable");
      }
      Content content =
          decodeContent(checkpoint.stored().eventId(), checkpoint.content(), Content.class);
      for (HistoryEntry entry : history) {
        StoredFact stored = entry.stored();
        if (stored.sequence() <= checkpoint.stored().sequence()
            || stored.sequence() > throughVersion) {
          continue;
        }
        Fact fact = stored.fact();
        switch (fact) {
          case DocumentUploaded uploaded ->
              content =
                  content.withFilename(
                      uploaded.documentId(),
                      decodeContent(stored.eventId(), entry.content(), Filename.class).fileName());
          case FilenameChanged changed ->
              content =
                  content.withFilename(
                      changed.documentId(),
                      decodeContent(stored.eventId(), entry.content(), Filename.class).fileName());
          case DraftUpdated updated ->
              content =
                  content.withBody(decodeContent(stored.eventId(), entry.content(), Body.class));
          default -> {
            if (!(fact instanceof Reviewed || fact instanceof DraftContentCompacted)) {
              throw new IllegalStateException("Unsupported spike content transition");
            }
          }
        }
      }
      return new View(state, content);
    }

    void deleteDraftContent(UUID applicationId, long submissionVersion) {
      jdbc.update(
          "DELETE FROM spike.event_content c USING axon.domain_event_entry e "
              + "WHERE c.event_id = e.event_identifier AND e.aggregate_identifier = ? "
              + "AND e.sequence_number < ?",
          applicationId.toString(),
          submissionVersion);
    }

    void save(View view) {
      jdbc.update(
          "INSERT INTO spike.application_view (application_id, stream_version, payload) "
              + "VALUES (?, ?, CAST(? AS jsonb)) "
              + "ON CONFLICT (application_id) DO UPDATE "
              + "SET stream_version = EXCLUDED.stream_version, payload = EXCLUDED.payload "
              + "WHERE spike.application_view.stream_version < EXCLUDED.stream_version",
          view.state().applicationId(),
          view.state().version(),
          mapper.writeValueAsString(view));
    }

    View view(UUID applicationId) {
      String json =
          jdbc.queryForObject(
              "SELECT payload::text FROM spike.application_view WHERE application_id = ?",
              String.class,
              applicationId);
      return mapper.readValue(json, View.class);
    }

    void clearViews() {
      jdbc.update("DELETE FROM spike.application_view");
    }

    private Fact decode(String type, byte[] payload) {
      for (Class<? extends Fact> candidate :
          List.of(
              DraftStarted.class,
              DocumentUploaded.class,
              FilenameChanged.class,
              DraftUpdated.class,
              DeclarationPrepared.class,
              Submitted.class,
              Reviewed.class,
              DraftContentCompacted.class)) {
        if (new MessageType(candidate).qualifiedName().toString().equals(type)) {
          return mapper.readValue(payload, candidate);
        }
      }
      throw new IllegalStateException("Unsupported stored spike event type: " + type);
    }
  }

  @Namespace(PROCESSOR)
  @Transactional
  static class Projection {
    private final Store store;

    Projection(Store store) {
      this.store = store;
    }

    @EventHandler
    public void on(DraftStarted fact) {
      project(fact);
    }

    @EventHandler
    public void on(DocumentUploaded fact) {
      project(fact);
    }

    @EventHandler
    public void on(FilenameChanged fact) {
      project(fact);
    }

    @EventHandler
    public void on(DraftUpdated fact) {
      project(fact);
    }

    @EventHandler
    public void on(DeclarationPrepared fact) {
      project(fact);
    }

    @EventHandler
    public void on(Submitted fact) {
      project(fact);
    }

    @EventHandler
    public void on(Reviewed fact) {
      project(fact);
    }

    @EventHandler
    public void on(DraftContentCompacted fact) {
      project(fact);
    }

    public void project(Fact fact) {
      store.save(store.reconstructForProjection(fact.applicationId(), fact.version()));
    }

    @ResetHandler
    public void reset() {
      store.clearViews();
    }
  }

  static class StaleStreamVersion extends IllegalStateException {
    StaleStreamVersion() {
      super("Spike expected stream version is stale");
    }
  }

  static class DeclarationRequired extends IllegalStateException {
    DeclarationRequired() {
      super("Prepare and review the current spike draft before submitting");
    }
  }

  static class ReviewedContentChanged extends IllegalStateException {
    ReviewedContentChanged() {
      super("Spike draft content differs from the reviewed content hash");
    }
  }

  static class DraftHistoryCompacted extends IllegalStateException {
    DraftHistoryCompacted() {
      super("Spike draft content history intentionally compacted into submission");
    }
  }

  static class ForcedRollback extends IllegalStateException {
    ForcedRollback() {
      super("Spike failure after event append");
    }
  }

  static class WriteProbe {
    volatile UUID applicationId;
    volatile CyclicBarrier barrier;
    volatile boolean failAfterAppend;
    volatile CountDownLatch submissionEntered;
    volatile CountDownLatch releaseSubmission;

    void beforeAppend(Fact fact) {
      if (!fact.applicationId().equals(applicationId)) {
        return;
      }
      try {
        if (fact instanceof Submitted && submissionEntered != null && releaseSubmission != null) {
          submissionEntered.countDown();
          if (!releaseSubmission.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Spike submission gate timed out");
          }
        }
        if (barrier != null) {
          barrier.await(10, TimeUnit.SECONDS);
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted spike concurrency barrier", exception);
      } catch (BrokenBarrierException | TimeoutException exception) {
        throw new IllegalStateException("Spike concurrency barrier failed", exception);
      }
    }

    void afterAppend(UUID id) {
      if (id.equals(applicationId) && failAfterAppend) {
        throw new ForcedRollback();
      }
    }

    void reset() {
      applicationId = null;
      barrier = null;
      failAfterAppend = false;
      submissionEntered = null;
      releaseSubmission = null;
    }
  }
}
