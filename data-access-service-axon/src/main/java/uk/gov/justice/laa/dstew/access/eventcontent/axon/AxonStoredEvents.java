package uk.gov.justice.laa.dstew.access.eventcontent.axon;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateEventEntry;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentStore;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;
import uk.gov.justice.laa.dstew.access.eventcontent.RegisteredContentReaders;
import uk.gov.justice.laa.dstew.access.eventcontent.StoredEvents;

/**
 * Loads events from Axon's event table by identifier and rejoins their content in one batch. Axon
 * has no load-by-identifier API, so this decodes rows itself, by {@code @Event} name, with the same
 * mapper Axon's converter uses.
 */
final class AxonStoredEvents implements StoredEvents {

  private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {};

  private final Supplier<EntityManager> entityManager;
  private final Supplier<EventContentSeparator> separator;
  private final Supplier<EventContentStore> store;
  private final ObjectMapper mapper;
  private final Map<String, Class<?>> eventTypes;

  AxonStoredEvents(
      Supplier<EntityManager> entityManager,
      Supplier<EventContentSeparator> separator,
      Supplier<EventContentStore> store,
      EventJson json,
      Map<String, Class<?>> eventTypes) {
    this.entityManager = entityManager;
    this.separator = separator;
    this.store = store;
    this.mapper = json.mapper();
    this.eventTypes = Map.copyOf(eventTypes);
  }

  /**
   * Finds every {@code @Event} class under a package, keyed by its qualified event name.
   *
   * @throws IllegalStateException if two classes share a name
   */
  static Map<String, Class<?>> eventTypesIn(String basePackage) {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Event.class));
    Map<String, Class<?>> types = new HashMap<>();
    for (var candidate : scanner.findCandidateComponents(basePackage)) {
      Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
      Event event = type.getAnnotation(Event.class);
      String name = event.namespace() + "." + event.name();
      Class<?> previous = types.put(name, type);
      if (previous != null) {
        throw new IllegalStateException(
            "Event name " + name + " is used by " + previous + " and " + type);
      }
    }
    return types;
  }

  @Override
  public Map<String, EventMessage> load(String readerName, Collection<String> identifiers) {
    RegisteredContentReaders.require(readerName);
    if (identifiers.isEmpty()) {
      return Map.of();
    }
    List<AggregateEventEntry> entries =
        entityManager
            .get()
            .createQuery(
                "SELECT e FROM AggregateEventEntry e WHERE e.identifier IN :identifiers",
                AggregateEventEntry.class)
            .setParameter("identifiers", identifiers)
            .getResultList();
    Map<String, ObjectNode> content = store.get().findByEventIds(identifiers);
    Map<String, EventMessage> loaded = new HashMap<>();
    for (AggregateEventEntry entry : entries) {
      loaded.put(entry.identifier(), decode(entry, content.get(entry.identifier())));
    }
    return loaded;
  }

  private EventMessage decode(AggregateEventEntry entry, ObjectNode content) {
    Class<?> type = eventTypes.get(entry.type());
    if (type == null) {
      throw new IllegalStateException("No @Event class is named " + entry.type());
    }
    ObjectNode kept = (ObjectNode) mapper.readTree(entry.payload());
    if (content == null) {
      separator.get().requireNoContentFields(entry.type(), entry.identifier());
    }
    ObjectNode whole = content == null ? kept : separator.get().rejoin(kept, content);
    Map<String, String> metadata =
        entry.metadata() == null ? Map.of() : mapper.readValue(entry.metadata(), METADATA);
    return new GenericEventMessage(
        entry.identifier(),
        new MessageType(new QualifiedName(entry.type()), entry.version()),
        mapper.treeToValue(whole, type),
        metadata,
        entry.timestamp());
  }
}
