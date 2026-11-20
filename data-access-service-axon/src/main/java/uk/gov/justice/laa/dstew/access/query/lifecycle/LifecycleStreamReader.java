package uk.gov.justice.laa.dstew.access.query.lifecycle;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.axonframework.common.TypeReference;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateEventEntry;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Reads a genesis's lifecycle stream: its events, in the order they occurred, whichever aggregate
 * appended them. Axon has no load-by-identifier API, so stored rows are decoded here the same way
 * Axon's JPA storage engine decodes them.
 */
@Component
public class LifecycleStreamReader {

  static final String EVENTS_PACKAGE = "uk.gov.justice.laa.dstew.access.command";

  private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {};

  private final LifecycleStreamRepository repository;
  private final EventConverter converter;
  private final Map<String, Class<?>> eventTypes;
  private final EntityManager entityManager;

  /** Creates a reader that decodes every {@code @Event} type in the command package. */
  public LifecycleStreamReader(
      LifecycleStreamRepository repository, EventConverter converter, EntityManager entityManager) {
    this.repository = repository;
    this.converter = converter;
    this.entityManager = entityManager;
    this.eventTypes = eventTypesIn(EVENTS_PACKAGE, new AnnotationMessageTypeResolver());
  }

  /**
   * Returns the events in a genesis's lifecycle stream, in the order they occurred.
   *
   * @throws MissingStoredEventException if a pointer names an event that is not stored
   */
  public List<EventMessage> read(UUID genesisId) {
    List<String> identifiers =
        repository.findAllByGenesisIdOrderByOccurredAtAscIdAsc(genesisId).stream()
            .filter(pointer -> LifecycleStreamProjection.LOCAL_SOURCE.equals(pointer.getSource()))
            .map(LifecycleStreamEntry::getEventIdentifier)
            .toList();
    if (identifiers.isEmpty()) {
      return List.of();
    }
    Map<String, AggregateEventEntry> entries =
        entityManager
            .createQuery(
                "SELECT e FROM AggregateEventEntry e WHERE e.identifier IN :identifiers",
                AggregateEventEntry.class)
            .setParameter("identifiers", identifiers)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(AggregateEventEntry::identifier, Function.identity()));
    return identifiers.stream()
        .map(
            identifier -> {
              AggregateEventEntry entry = entries.get(identifier);
              if (entry == null) {
                throw new MissingStoredEventException(genesisId, identifier);
              }
              return decode(entry);
            })
        .toList();
  }

  private EventMessage decode(AggregateEventEntry entry) {
    Class<?> type = eventTypes.get(entry.type());
    if (type == null) {
      throw new IllegalStateException("No @Event class is named " + entry.type());
    }
    Map<String, String> metadata =
        entry.metadata() == null
            ? Map.of()
            : converter.convert(entry.metadata(), METADATA.getType());
    EventMessage stored =
        new GenericEventMessage(
            entry.identifier(),
            new MessageType(entry.type(), entry.version()),
            entry.payload(),
            metadata,
            entry.timestamp());
    return converter.convertEvent(stored, type);
  }

  /**
   * Finds every {@code @Event} class under a package, keyed by the event name Axon stores.
   *
   * @throws IllegalStateException if two classes share a name
   */
  static Map<String, Class<?>> eventTypesIn(String basePackage, MessageTypeResolver resolver) {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Event.class));
    Map<String, Class<?>> types = new HashMap<>();
    for (var candidate : scanner.findCandidateComponents(basePackage)) {
      Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
      String name = resolver.resolveOrThrow(type).name();
      Class<?> previous = types.put(name, type);
      if (previous != null) {
        throw new IllegalStateException(
            "Event name " + name + " is used by " + previous + " and " + type);
      }
    }
    return Map.copyOf(types);
  }
}
