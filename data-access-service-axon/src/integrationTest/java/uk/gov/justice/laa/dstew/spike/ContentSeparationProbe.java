package uk.gov.justice.laa.dstew.spike;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GenericTaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.extension.spring.config.SpringEventSourcedEntityConfigurer;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.EventCriterion;
import org.axonframework.messaging.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Gate 4a step 2: one whole entity holds erasable content; a storage-engine decorator keeps only
 * schema-listed fields on the stored event and moves everything else to a content table. Test-only.
 */
final class ContentSeparationProbe {

  static final String TAG = "ProbeDraft";
  static final String NAMESPACE = "probe";
  static final String SCHEMA_ROOT = "spike/event-schemas/";
  static final String KEPT_ONLY_PROCESSOR = "probe-kept-only";
  static final String CONTENT_READER_PROCESSOR = "probe-content-reader";

  /** The registered content readers. In production: a reviewed list, shared with compaction. */
  static final Set<String> REGISTERED_CONTENT_READERS = Set.of(CONTENT_READER_PROCESSOR);

  private ContentSeparationProbe() {}

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {

    @Bean
    ConfigurationEnhancer probeDraftEntity() {
      return new SpringEventSourcedEntityConfigurer<>(ProbeDraft.class, UUID.class);
    }

    @Bean
    RaceGate probeRaceGate() {
      return new RaceGate();
    }

    @Bean
    ProbeHandlers probeHandlers(RaceGate gate) {
      return new ProbeHandlers(gate);
    }

    @Bean
    EventJson eventJson() {
      return new EventJson();
    }

    // Axon serialises events with the mapper we own, so the split and Axon agree exactly.
    @Bean
    EventConverter eventConverter(EventJson json) {
      return new DelegatingEventConverter(new JacksonConverter(json.mapper()));
    }

    @Bean
    KeptFieldSchemas keptFieldSchemas(EventJson json) {
      return new KeptFieldSchemas(json.mapper());
    }

    @Bean
    ProbeQueries probeQueries() {
      return new ProbeQueries();
    }

    @Bean
    KeptOnlyProjection keptOnlyProjection() {
      return new KeptOnlyProjection();
    }

    @Bean
    ContentReaderProjection contentReaderProjection() {
      return new ContentReaderProjection();
    }

    @Bean
    EventProcessorDefinition keptOnlyProcessor() {
      return EventProcessorDefinition.pooledStreamingMatching(KEPT_ONLY_PROCESSOR)
          .customized(config -> config.errorHandler(PropagatingErrorHandler.instance()));
    }

    @Bean
    EventProcessorDefinition contentReaderProcessor(
        ObjectProvider<JdbcTemplate> jdbc,
        ObjectProvider<EventJson> json,
        ObjectProvider<EventConverter> converter) {
      return EventProcessorDefinition.pooledStreamingMatching(CONTENT_READER_PROCESSOR)
          .customized(
              config ->
                  config
                      .eventSource(
                          ContentReaders.sourceFor(
                              CONTENT_READER_PROCESSOR,
                              config.eventSource(),
                              jdbc,
                              json,
                              converter))
                      .errorHandler(PropagatingErrorHandler.instance()));
    }

    @Bean
    SeparationObservations separationObservations() {
      return new SeparationObservations();
    }

    @Bean
    ConfigurationEnhancer contentSeparatingDecorator(
        ObjectProvider<JdbcTemplate> jdbc,
        ObjectProvider<EventJson> mapper,
        ObjectProvider<KeptFieldSchemas> schemas,
        SeparationObservations observations) {
      return registry ->
          registry.registerDecorator(
              EventStorageEngine.class,
              0,
              (config, name, delegate) ->
                  new ContentSeparatingEngine(
                      delegate,
                      jdbc,
                      mapper,
                      schemas,
                      observations,
                      () -> config.getComponent(EventConverter.class)));
    }
  }

  /**
   * The one JSON mapper for events, owned by us rather than by Axon or Spring. Decimals stay exact
   * through JSON trees, which the split relies on.
   */
  static final class EventJson {
    private final ObjectMapper mapper =
        JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    ObjectMapper mapper() {
      return mapper;
    }
  }

  /** Content that may be erased. Erased is a state, not a null; JSON carries only the value. */
  static final class Erasable<T> {
    private static final Erasable<?> ERASED = new Erasable<>(null);

    private final @Nullable T value;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    Erasable(@Nullable T value) {
      this.value = value;
    }

    static <T> Erasable<T> of(T value) {
      return new Erasable<>(value);
    }

    @SuppressWarnings("unchecked")
    static <T> Erasable<T> erased() {
      return (Erasable<T>) ERASED;
    }

    static <T> Erasable<T> orErased(@Nullable Erasable<T> erasable) {
      return erasable == null ? erased() : erasable;
    }

    boolean isErased() {
      return value == null;
    }

    T value() {
      if (value == null) {
        throw new ContentErasedException();
      }
      return value;
    }

    @JsonValue
    @Nullable T jsonValue() {
      return value;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Erasable<?> that && java.util.Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hashCode(value);
    }

    @Override
    public String toString() {
      return isErased() ? "Erasable[erased]" : "Erasable[present]";
    }
  }

  static final class ContentErasedException extends IllegalStateException {
    ContentErasedException() {
      super("Content has been erased");
    }
  }

  record ProbeBody(
      String clientName,
      String postcode,
      LocalDate dateOfBirth,
      BigDecimal income,
      @Nullable String middleName,
      @Nullable Map<String, Object> application) {}

  @Event(namespace = NAMESPACE, name = "ProbeDraftStarted")
  record ProbeDraftStarted(
      @EventTag(key = TAG) UUID draftId,
      String officeCode,
      Instant startedAt,
      Erasable<ProbeBody> body) {
    ProbeDraftStarted {
      body = Erasable.orErased(body);
    }
  }

  @Event(namespace = NAMESPACE, name = "ProbeDraftUpdated")
  record ProbeDraftUpdated(
      @EventTag(key = TAG) UUID draftId, int revision, Erasable<ProbeBody> body) {
    ProbeDraftUpdated {
      body = Erasable.orErased(body);
    }
  }

  @Event(namespace = NAMESPACE, name = "ProbeDraftSubmitted")
  record ProbeDraftSubmitted(@EventTag(key = TAG) UUID draftId, int revision) {}

  // Deliberately has no kept-field schema.
  @Event(namespace = NAMESPACE, name = "ProbeNoteAdded")
  record ProbeNoteAdded(@EventTag(key = TAG) UUID draftId, String note) {}

  @Command(routingKey = "draftId")
  record StartProbe(UUID draftId, String officeCode, Instant startedAt, ProbeBody body) {}

  @Command(routingKey = "draftId")
  record UpdateProbe(UUID draftId, ProbeBody body) {}

  // Routed by attempt, not draft, so two updates to one draft run concurrently.
  @Command(routingKey = "attemptId")
  record RacingUpdateProbe(UUID draftId, UUID attemptId, ProbeBody body) {}

  @Command(routingKey = "draftId")
  record SubmitProbe(UUID draftId) {}

  @Command(routingKey = "draftId")
  record ReadProbe(UUID draftId) {}

  @Command(routingKey = "draftId")
  record AddProbeNote(UUID draftId, String note) {}

  record ProbeDraftQuery(UUID draftId) {}

  record ProbeState(
      boolean exists,
      @Nullable String officeCode,
      int revision,
      boolean submitted,
      Erasable<ProbeBody> body) {
    static final ProbeState EMPTY = new ProbeState(false, null, 0, false, Erasable.erased());
  }

  /** Pure functions, as production's ApplicationEvolve and ApplicationDecider. */
  static final class ProbeRules {
    private ProbeRules() {}

    static ProbeState evolve(ProbeState state, Object event) {
      return switch (event) {
        case ProbeDraftStarted e -> new ProbeState(true, e.officeCode(), 0, false, e.body());
        case ProbeDraftUpdated e ->
            new ProbeState(true, state.officeCode(), e.revision(), state.submitted(), e.body());
        case ProbeDraftSubmitted e ->
            new ProbeState(true, state.officeCode(), e.revision(), true, state.body());
        default -> state;
      };
    }

    static ProbeState fold(List<?> events) {
      ProbeState state = ProbeState.EMPTY;
      for (Object event : events) {
        state = evolve(state, event);
      }
      return state;
    }

    static List<Object> decideSubmit(ProbeState state, UUID draftId) {
      if (!state.exists()) {
        throw new IllegalStateException("Probe draft does not exist");
      }
      if (state.submitted()) {
        throw new IllegalStateException("Probe draft already submitted");
      }
      return List.of(new ProbeDraftSubmitted(draftId, state.revision()));
    }

    // Deliberately wrong: a command-side decision that depends on erasable content.
    static List<Object> decideSubmitRequiringClientName(ProbeState state, UUID draftId) {
      if (state.body().value().clientName().isBlank()) {
        throw new IllegalStateException("Client name required");
      }
      return decideSubmit(state, draftId);
    }
  }

  @EventSourced(tagKey = TAG, idType = UUID.class)
  static class ProbeDraft {
    private ProbeState state = ProbeState.EMPTY;

    @EntityCreator
    protected ProbeDraft() {}

    @EventSourcingHandler
    void on(ProbeDraftStarted event) {
      state = ProbeRules.evolve(state, event);
    }

    @EventSourcingHandler
    void on(ProbeDraftUpdated event) {
      state = ProbeRules.evolve(state, event);
    }

    @EventSourcingHandler
    void on(ProbeDraftSubmitted event) {
      state = ProbeRules.evolve(state, event);
    }

    ProbeState state() {
      return state;
    }
  }

  /** Holds racing handlers after they load the entity, so both append at the same version. */
  static final class RaceGate {
    volatile java.util.concurrent.@Nullable CyclicBarrier barrier;

    void await() {
      java.util.concurrent.CyclicBarrier current = barrier;
      if (current == null) {
        return;
      }
      try {
        current.await(10, java.util.concurrent.TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted probe race", e);
      } catch (java.util.concurrent.BrokenBarrierException
          | java.util.concurrent.TimeoutException e) {
        throw new IllegalStateException("Probe race barrier failed", e);
      }
    }
  }

  static class ProbeHandlers {
    private final RaceGate gate;

    ProbeHandlers(RaceGate gate) {
      this.gate = gate;
    }

    @CommandHandler
    void start(
        StartProbe command,
        @InjectEntity(idProperty = "draftId") @Nullable ProbeDraft draft,
        EventAppender appender) {
      if (draft != null && draft.state().exists()) {
        throw new IllegalStateException("Probe draft already exists");
      }
      appender.append(
          new ProbeDraftStarted(
              command.draftId(),
              command.officeCode(),
              command.startedAt(),
              Erasable.of(command.body())));
    }

    @CommandHandler
    void update(
        UpdateProbe command,
        @InjectEntity(idProperty = "draftId") ProbeDraft draft,
        EventAppender appender) {
      appender.append(
          new ProbeDraftUpdated(
              command.draftId(), draft.state().revision() + 1, Erasable.of(command.body())));
    }

    @CommandHandler
    void racingUpdate(
        RacingUpdateProbe command,
        @InjectEntity(idProperty = "draftId") ProbeDraft draft,
        EventAppender appender) {
      int revision = draft.state().revision() + 1;
      gate.await();
      appender.append(
          new ProbeDraftUpdated(command.draftId(), revision, Erasable.of(command.body())));
    }

    @CommandHandler
    void submit(
        SubmitProbe command,
        @InjectEntity(idProperty = "draftId") ProbeDraft draft,
        EventAppender appender) {
      appender.append(ProbeRules.decideSubmit(draft.state(), command.draftId()));
    }

    @CommandHandler
    ProbeState read(ReadProbe command, @InjectEntity(idProperty = "draftId") ProbeDraft draft) {
      return draft.state();
    }

    @CommandHandler
    void note(AddProbeNote command, EventAppender appender) {
      appender.append(new ProbeNoteAdded(command.draftId(), command.note()));
    }
  }

  /** Hands out content-joining event sources, but only to registered processors. */
  static final class ContentReaders {
    private ContentReaders() {}

    static StreamableEventSource sourceFor(
        String processor,
        StreamableEventSource delegate,
        ObjectProvider<JdbcTemplate> jdbc,
        ObjectProvider<EventJson> json,
        ObjectProvider<EventConverter> converter) {
      if (!REGISTERED_CONTENT_READERS.contains(processor)) {
        throw new IllegalStateException(processor + " is not a registered content reader");
      }
      return new ContentReadingEventSource(delegate, jdbc, json, converter);
    }
  }

  static final class ContentReadingEventSource implements StreamableEventSource {
    private final StreamableEventSource delegate;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final ObjectProvider<EventJson> json;
    private final ObjectProvider<EventConverter> converter;

    ContentReadingEventSource(
        StreamableEventSource delegate,
        ObjectProvider<JdbcTemplate> jdbc,
        ObjectProvider<EventJson> json,
        ObjectProvider<EventConverter> converter) {
      this.delegate = delegate;
      this.jdbc = jdbc;
      this.json = json;
      this.converter = converter;
    }

    @Override
    public MessageStream<EventMessage> open(
        StreamingCondition condition, ProcessingContext context) {
      return delegate.open(condition, context).mapMessage(this::join);
    }

    private EventMessage join(EventMessage message) {
      if (message == null || message.payload() == null) {
        return message;
      }
      List<String> rows =
          jdbc.getObject()
              .queryForList(
                  "SELECT content::text FROM spike.probe_event_content WHERE event_id = ?",
                  String.class,
                  message.identifier());
      if (rows.isEmpty()) {
        return message;
      }
      EventConverter axon = converter.getObject();
      ObjectNode payload = (ObjectNode) axon.convert(message.payload(), JsonNode.class);
      payload.setAll((ObjectNode) json.getObject().mapper().readTree(rows.getFirst()));
      return new GenericEventMessage(
          message.identifier(),
          message.type(),
          axon.convert(payload, byte[].class),
          message.metadata(),
          message.timestamp());
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken(ProcessingContext context) {
      return delegate.firstToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(ProcessingContext context) {
      return delegate.latestToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(Instant at, ProcessingContext context) {
      return delegate.tokenAt(at, context);
    }
  }

  /** Read side built from the entity, with no stored read model. */
  static class ProbeQueries {

    @QueryHandler
    ProbeState handle(
        ProbeDraftQuery query, @InjectEntity(idProperty = "draftId") ProbeDraft draft) {
      return draft.state();
    }
  }

  @Namespace(KEPT_ONLY_PROCESSOR)
  static class KeptOnlyProjection {
    final Map<UUID, Erasable<ProbeBody>> seen = new ConcurrentHashMap<>();

    @EventHandler
    void on(ProbeDraftStarted event) {
      seen.put(event.draftId(), event.body());
    }
  }

  @Namespace(CONTENT_READER_PROCESSOR)
  static class ContentReaderProjection {
    final Map<UUID, Erasable<ProbeBody>> seen = new ConcurrentHashMap<>();

    @EventHandler
    void on(ProbeDraftStarted event) {
      seen.put(event.draftId(), event.body());
    }
  }

  record KeptFieldSchema(String typeName, Set<String> keptFields, Schema schema) {

    List<String> violations(JsonNode kept) {
      List<Error> errors =
          schema.validate(
              kept.toString(),
              InputFormat.JSON,
              context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
      return errors.stream().map(Error::toString).toList();
    }
  }

  /**
   * One JSON Schema of kept fields per event type name. Only listed fields stay on the stored
   * event; everything else is content. Schemas must forbid additional properties.
   */
  static final class KeptFieldSchemas {
    private final ObjectMapper mapper;
    private final Map<String, Optional<KeptFieldSchema>> cache = new ConcurrentHashMap<>();
    private final SchemaRegistry registry =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    KeptFieldSchemas(ObjectMapper mapper) {
      this.mapper = mapper;
    }

    Optional<KeptFieldSchema> find(String typeName) {
      return cache.computeIfAbsent(typeName, this::load);
    }

    private Optional<KeptFieldSchema> load(String typeName) {
      String path = SCHEMA_ROOT + typeName + ".json";
      JsonNode definition;
      try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
        if (in == null) {
          return Optional.empty();
        }
        definition = mapper.readTree(in);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot read kept-field schema " + path, e);
      }
      JsonNode additional = definition.get("additionalProperties");
      if (additional == null || !additional.isBoolean() || additional.booleanValue()) {
        throw new IllegalStateException(path + " must set additionalProperties to false");
      }
      Set<String> kept = new LinkedHashSet<>(definition.get("properties").propertyNames());
      Schema schema = registry.getSchema(new SchemaLocation(new AbsoluteIri("classpath:" + path)));
      return Optional.of(new KeptFieldSchema(typeName, kept, schema));
    }

    /** Splits a payload into kept fields and content. Unlisted fields always become content. */
    Split split(KeptFieldSchema schema, ObjectNode payload) {
      ObjectNode kept = mapper.createObjectNode();
      ObjectNode content = mapper.createObjectNode();
      for (Map.Entry<String, JsonNode> field : payload.properties()) {
        if (schema.keptFields().contains(field.getKey())) {
          kept.set(field.getKey(), field.getValue());
        } else {
          content.set(field.getKey(), field.getValue());
        }
      }
      return new Split(kept, content);
    }

    /** Test helper: what an event looks like after its content has been erased. */
    <T> T erase(T event) {
      String typeName = NAMESPACE + "." + event.getClass().getAnnotation(Event.class).name();
      KeptFieldSchema schema =
          find(typeName).orElseThrow(() -> new UnclassifiedEventException(typeName));
      ObjectNode kept = split(schema, (ObjectNode) mapper.valueToTree(event)).kept();
      @SuppressWarnings("unchecked")
      Class<T> type = (Class<T>) event.getClass();
      return mapper.treeToValue(kept, type);
    }
  }

  record Split(ObjectNode kept, ObjectNode content) {}

  static final class UnclassifiedEventException extends IllegalStateException {
    UnclassifiedEventException(String typeName) {
      super("No kept-field schema for event type " + typeName);
    }
  }

  static final class KeptFieldViolationException extends IllegalStateException {
    KeptFieldViolationException(String typeName, List<String> violations) {
      super("Kept fields of " + typeName + " violate schema: " + violations);
    }
  }

  static final class SeparationObservations {
    final List<String> appendedTypeNames = new CopyOnWriteArrayList<>();
    final Set<String> reportedUnclassified = ConcurrentHashMap.newKeySet();
    final Set<String> metadataKeys = ConcurrentHashMap.newKeySet();
    final List<String> loadedPayloadClasses = new CopyOnWriteArrayList<>();
    final AtomicInteger contentQueries = new AtomicInteger();
    volatile @Nullable EventConverter axonConverter;

    void reset() {
      appendedTypeNames.clear();
      reportedUnclassified.clear();
      metadataKeys.clear();
      loadedPayloadClasses.clear();
      contentQueries.set(0);
    }

    void print(String label) {
      System.out.printf(
          "PROBE2 %s appendedTypeNames=%s reportedUnclassified=%s metadataKeys=%s"
              + " loadedPayloadClasses=%s contentQueries=%d%n",
          label,
          appendedTypeNames,
          reportedUnclassified,
          metadataKeys,
          loadedPayloadClasses,
          contentQueries.get());
    }
  }

  static final class ContentSeparatingEngine implements EventStorageEngine {
    private final EventStorageEngine delegate;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final ObjectProvider<EventJson> mapper;
    private final ObjectProvider<KeptFieldSchemas> schemas;
    private final SeparationObservations observations;
    private final Supplier<EventConverter> converter;

    ContentSeparatingEngine(
        EventStorageEngine delegate,
        ObjectProvider<JdbcTemplate> jdbc,
        ObjectProvider<EventJson> mapper,
        ObjectProvider<KeptFieldSchemas> schemas,
        SeparationObservations observations,
        Supplier<EventConverter> converter) {
      this.delegate = delegate;
      this.jdbc = jdbc;
      this.mapper = mapper;
      this.schemas = schemas;
      this.observations = observations;
      this.converter = converter;
    }

    @Override
    public CompletableFuture<AppendTransaction<?>> appendEvents(
        AppendCondition condition, ProcessingContext context, List<TaggedEventMessage<?>> events) {
      List<TaggedEventMessage<?>> separated = new ArrayList<>();
      List<Object[]> contentRows = new ArrayList<>();
      try {
        for (TaggedEventMessage<?> tagged : events) {
          separated.add(separate(tagged, contentRows));
        }
      } catch (RuntimeException e) {
        return CompletableFuture.failedFuture(e);
      }
      // Inside the append's database transaction, so content and event commit or roll back
      // together.
      for (Object[] row : contentRows) {
        jdbc.getObject()
            .update(
                "INSERT INTO spike.probe_event_content (event_id, tag, content)"
                    + " VALUES (?, ?, ?::jsonb)",
                row);
      }
      return delegate.appendEvents(condition, context, separated);
    }

    private TaggedEventMessage<?> separate(
        TaggedEventMessage<?> tagged, List<Object[]> contentRows) {
      EventMessage event = tagged.event();
      String typeName = event.type().name();
      observations.appendedTypeNames.add(typeName);
      observations.metadataKeys.addAll(event.metadata().keySet());
      Optional<KeptFieldSchema> found = schemas.getObject().find(typeName);
      if (found.isEmpty()) {
        if (typeName.startsWith(NAMESPACE + ".")) {
          throw new UnclassifiedEventException(typeName);
        }
        // Report-only during rollout: existing event types pass through unchanged.
        observations.reportedUnclassified.add(typeName);
        return tagged;
      }
      KeptFieldSchema schema = found.get();
      // Axon's own converter, so the split matches exactly what Axon would have stored.
      EventConverter axon = converter.get();
      observations.axonConverter = axon;
      Split split =
          schemas
              .getObject()
              .split(schema, (ObjectNode) axon.convert(event.payload(), JsonNode.class));
      List<String> violations = schema.violations(split.kept());
      if (!violations.isEmpty()) {
        throw new KeptFieldViolationException(typeName, violations);
      }
      if (!split.content().isEmpty()) {
        contentRows.add(
            new Object[] {event.identifier(), tagKey(tagged.tags()), split.content().toString()});
      }
      EventMessage kept =
          new GenericEventMessage(
              event.identifier(),
              event.type(),
              axon.convert(split.kept(), byte[].class),
              event.metadata(),
              event.timestamp());
      return new GenericTaggedEventMessage<>(kept, tagged.tags());
    }

    @Override
    public MessageStream<EventMessage> source(
        SourcingCondition condition, ProcessingContext context) {
      Map<String, ObjectNode> content = loadContent(condition);
      return delegate.source(condition, context).mapMessage(rejoin(content));
    }

    private Map<String, ObjectNode> loadContent(SourcingCondition condition) {
      String[] tags =
          condition.criteria().flatten().stream()
              .map(EventCriterion::tags)
              .flatMap(Set::stream)
              .map(ContentSeparatingEngine::tagKey)
              .toArray(String[]::new);
      Map<String, ObjectNode> content = new HashMap<>();
      if (tags.length == 0) {
        return content;
      }
      observations.contentQueries.incrementAndGet();
      ObjectMapper json = mapper.getObject().mapper();
      jdbc.getObject()
          .query(
              "SELECT event_id, content::text AS content FROM spike.probe_event_content"
                  + " WHERE tag = ANY (?)",
              row -> {
                content.put(
                    row.getString("event_id"),
                    (ObjectNode) json.readTree(row.getString("content")));
              },
              (Object) tags);
      return content;
    }

    private Function<EventMessage, EventMessage> rejoin(Map<String, ObjectNode> content) {
      return message -> {
        if (message == null || message.payload() == null) {
          return message;
        }
        observations.loadedPayloadClasses.add(message.payload().getClass().getSimpleName());
        ObjectNode extra = content.get(message.identifier());
        if (extra == null) {
          // No row: either the event had no content or it has been erased.
          return message;
        }
        EventConverter axon = converter.get();
        ObjectNode payload = (ObjectNode) axon.convert(message.payload(), JsonNode.class);
        payload.setAll(extra);
        return new GenericEventMessage(
            message.identifier(),
            message.type(),
            axon.convert(payload, byte[].class),
            message.metadata(),
            message.timestamp());
      };
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

    private static String tagKey(Set<Tag> tags) {
      return tags.stream()
          .filter(tag -> TAG.equals(tag.key()))
          .map(ContentSeparatingEngine::tagKey)
          .findFirst()
          .orElse(null);
    }

    private static String tagKey(Tag tag) {
      return tag.key() + "=" + tag.value();
    }
  }
}
