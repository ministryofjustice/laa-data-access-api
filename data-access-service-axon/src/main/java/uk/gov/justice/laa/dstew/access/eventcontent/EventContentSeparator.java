package uk.gov.justice.laa.dstew.access.eventcontent;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Splits event payloads into retained fields and content. A field stays on the stored event only
 * when its record component is marked {@link RetainedField}; every other field is content.
 * Fail-safe: an unmarked field is content, and an event type outside the separator's event classes
 * is rejected.
 */
public final class EventContentSeparator {

  /**
   * The only metadata keys an event may carry. The calling service and the acting user's id are
   * kept because they identify the actor, not a data subject.
   */
  public static final Set<String> ALLOWED_METADATA_KEYS =
      Set.of("causationId", "correlationId", "X-Service-Name", "authenticatedUserId");

  private final ObjectMapper mapper;
  private final Map<String, Class<?>> eventTypes;
  private final Map<String, Fields> fields = new ConcurrentHashMap<>();

  /** Creates a separator for the given event classes, keyed by stored event type name. */
  public EventContentSeparator(EventJson json, Map<String, Class<?>> eventTypes) {
    this.mapper = json.mapper();
    this.eventTypes = Map.copyOf(eventTypes);
  }

  /** Kept fields and content of one event payload. Content may be empty. */
  public record Split(ObjectNode kept, ObjectNode content) {}

  /**
   * Splits a payload into the fields its event class marks {@link RetainedField} and content.
   *
   * @throws UnclassifiedEventException if the event type is not one of the separator's classes
   * @throws IllegalStateException if the event class is not a record
   */
  public Split separate(String typeName, ObjectNode payload) {
    Set<String> retained = fields(typeName).retained();
    ObjectNode kept = mapper.createObjectNode();
    ObjectNode content = mapper.createObjectNode();
    for (Map.Entry<String, JsonNode> field : payload.properties()) {
      if (retained.contains(field.getKey())) {
        kept.set(field.getKey(), field.getValue());
      } else {
        content.set(field.getKey(), field.getValue());
      }
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
   * Whether events of this type have content fields: record components not marked {@link
   * RetainedField}. Every such event has a content row, and erasure keeps it. A type outside the
   * separator's event classes cannot be appended, and is treated as having none when read.
   *
   * @throws IllegalStateException if the event class is not a record
   */
  public boolean hasContentFields(String typeName) {
    return eventTypes.containsKey(typeName) && fields(typeName).hasContent();
  }

  /**
   * Accepts an event without a content row only if its type has no content fields.
   *
   * @throws MissingEventContentException if the type has content fields
   */
  public void requireNoContentFields(String typeName, String eventId) {
    if (hasContentFields(typeName)) {
      throw new MissingEventContentException(typeName, eventId);
    }
  }

  private Fields fields(String typeName) {
    return fields.computeIfAbsent(typeName, this::classify);
  }

  private Fields classify(String typeName) {
    Class<?> type = eventTypes.get(typeName);
    if (type == null) {
      throw new UnclassifiedEventException(typeName);
    }
    if (!type.isRecord()) {
      throw new IllegalStateException("Event " + typeName + " must be a record");
    }
    RecordComponent[] components = type.getRecordComponents();
    Set<String> retained =
        Arrays.stream(components)
            .filter(component -> component.isAnnotationPresent(RetainedField.class))
            .map(RecordComponent::getName)
            .collect(Collectors.toUnmodifiableSet());
    return new Fields(retained, retained.size() < components.length);
  }

  private record Fields(Set<String> retained, boolean hasContent) {}

  /** Thrown when an event type is not one of the separator's event classes. */
  public static final class UnclassifiedEventException extends IllegalStateException {
    public UnclassifiedEventException(String typeName) {
      super("Unknown event type " + typeName);
    }
  }

  /** Thrown when an event carries metadata keys outside {@link #ALLOWED_METADATA_KEYS}. */
  public static final class KeptFieldViolationException extends IllegalStateException {
    public KeptFieldViolationException(String typeName, List<String> violations) {
      super("Event " + typeName + " keeps fields that are not allowed: " + violations);
    }
  }
}
