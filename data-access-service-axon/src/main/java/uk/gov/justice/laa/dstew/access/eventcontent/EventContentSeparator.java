package uk.gov.justice.laa.dstew.access.eventcontent;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Splits event payloads into kept fields and content using one JSON Schema of kept fields per event
 * type. Fail-safe: only fields a schema lists stay on the stored event, everything else is content,
 * and an event type without a schema is rejected.
 */
public final class EventContentSeparator {

  /** Classpath folder holding one {@code <event type name>.json} schema per event type. */
  public static final String SCHEMA_ROOT = "event-schemas/";

  /**
   * The only metadata keys an event may carry. The calling service and the acting user's id are
   * kept because they identify the actor, not a data subject.
   */
  public static final Set<String> ALLOWED_METADATA_KEYS =
      Set.of("causationId", "correlationId", "X-Service-Name", "authenticatedUserId");

  private final ObjectMapper mapper;
  private final Map<String, Optional<KeptFieldSchema>> cache = new ConcurrentHashMap<>();
  private final SchemaRegistry registry =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

  private final Map<String, Class<?>> eventTypes;
  private final Map<String, Boolean> contentFields = new ConcurrentHashMap<>();

  /** Creates a separator for the given event classes, keyed by qualified event name. */
  public EventContentSeparator(EventJson json, Map<String, Class<?>> eventTypes) {
    this.mapper = json.mapper();
    this.eventTypes = Map.copyOf(eventTypes);
  }

  /** Kept fields and content of one event payload. Content may be empty. */
  public record Split(ObjectNode kept, ObjectNode content) {}

  /**
   * Splits a payload, validating the kept fields against the event type's schema.
   *
   * @throws UnclassifiedEventException if the event type has no schema
   * @throws KeptFieldViolationException if the kept fields break the schema
   */
  public Split separate(String typeName, ObjectNode payload) {
    KeptFieldSchema schema = require(typeName);
    ObjectNode kept = mapper.createObjectNode();
    ObjectNode content = mapper.createObjectNode();
    for (Map.Entry<String, JsonNode> field : payload.properties()) {
      if (schema.keptFields().contains(field.getKey())) {
        kept.set(field.getKey(), field.getValue());
      } else {
        content.set(field.getKey(), field.getValue());
      }
    }
    List<String> violations = schema.violations(kept);
    if (!violations.isEmpty()) {
      throw new KeptFieldViolationException(typeName, violations);
    }
    return new Split(kept, content);
  }

  /** Rejoins stored kept fields with their content. */
  public ObjectNode rejoin(ObjectNode kept, ObjectNode content) {
    ObjectNode whole = kept.deepCopy();
    whole.setAll(content);
    return whole;
  }

  /**
   * Rejects metadata keys outside {@link #ALLOWED_METADATA_KEYS}.
   *
   * @throws KeptFieldViolationException if any other key is present
   */
  public void checkMetadata(String typeName, Set<String> keys) {
    List<String> unexpected =
        keys.stream().filter(key -> !ALLOWED_METADATA_KEYS.contains(key)).sorted().toList();
    if (!unexpected.isEmpty()) {
      throw new KeptFieldViolationException(
          typeName, List.of("metadata keys not allowed: " + unexpected));
    }
  }

  /** Returns the event as it loads once its content has been erased. */
  public <T> T erase(String typeName, T event) {
    ObjectNode payload = (ObjectNode) mapper.valueToTree(event);
    @SuppressWarnings("unchecked")
    Class<T> type = (Class<T>) event.getClass();
    return mapper.treeToValue(separate(typeName, payload).kept(), type);
  }

  /**
   * Whether events of this type store content, so a missing content row means lost content rather
   * than an event without content. Erasure keeps the row, so it never explains a missing one.
   */
  public boolean hasContentFields(String typeName) {
    return contentFields.computeIfAbsent(typeName, this::detectContentFields);
  }

  /**
   * Fails because an event of a type with content fields has no content row.
   *
   * @throws MissingEventContentException always
   */
  public void requireNoContentFields(String typeName, String eventId) {
    if (hasContentFields(typeName)) {
      throw new MissingEventContentException(typeName, eventId);
    }
  }

  private boolean detectContentFields(String typeName) {
    Class<?> type = eventTypes.get(typeName);
    Optional<KeptFieldSchema> schema = find(typeName);
    if (type == null || schema.isEmpty()) {
      return false;
    }
    if (!type.isRecord()) {
      throw new IllegalStateException("Event " + typeName + " must be a record");
    }
    return Arrays.stream(type.getRecordComponents())
        .map(RecordComponent::getName)
        .anyMatch(name -> !schema.get().keptFields().contains(name));
  }

  /** Whether an event type has a kept-field schema. */
  public boolean isClassified(String typeName) {
    return find(typeName).isPresent();
  }

  private KeptFieldSchema require(String typeName) {
    return find(typeName).orElseThrow(() -> new UnclassifiedEventException(typeName));
  }

  private Optional<KeptFieldSchema> find(String typeName) {
    return cache.computeIfAbsent(typeName, this::load);
  }

  private Optional<KeptFieldSchema> load(String typeName) {
    String path = SCHEMA_ROOT + typeName + ".json";
    JsonNode definition;
    try (InputStream in = EventContentSeparator.class.getClassLoader().getResourceAsStream(path)) {
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
    JsonNode properties = definition.get("properties");
    if (properties == null || !properties.isObject()) {
      throw new IllegalStateException(path + " must list its kept fields under properties");
    }
    Set<String> kept = new LinkedHashSet<>(properties.propertyNames());
    Schema schema = registry.getSchema(new SchemaLocation(new AbsoluteIri("classpath:" + path)));
    return Optional.of(new KeptFieldSchema(kept, schema));
  }

  private record KeptFieldSchema(Set<String> keptFields, Schema schema) {

    List<String> violations(JsonNode kept) {
      List<Error> errors =
          schema.validate(
              kept.toString(),
              InputFormat.JSON,
              context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
      return errors.stream().map(Error::toString).toList();
    }
  }

  /** Thrown when an event type has no kept-field schema. */
  public static final class UnclassifiedEventException extends IllegalStateException {
    public UnclassifiedEventException(String typeName) {
      super("No kept-field schema for event type " + typeName);
    }
  }

  /** Thrown when an event whose type has content fields has no content row. */
  public static final class MissingEventContentException extends IllegalStateException {
    public MissingEventContentException(String typeName, String eventId) {
      super("Event " + eventId + " of type " + typeName + " has no content row");
    }
  }

  /** Thrown when an event's kept fields or metadata break its schema. */
  public static final class KeptFieldViolationException extends IllegalStateException {
    public KeptFieldViolationException(String typeName, List<String> violations) {
      super("Kept fields of " + typeName + " violate schema: " + violations);
    }
  }
}
