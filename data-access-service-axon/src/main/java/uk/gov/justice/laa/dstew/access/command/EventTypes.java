package uk.gov.justice.laa.dstew.access.command;

import java.util.HashMap;
import java.util.Map;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/** Finds the stored event classes, keyed by the event name Axon stores. */
public final class EventTypes {

  /** The package holding every {@code @Event} class. */
  public static final String EVENTS_PACKAGE = "uk.gov.justice.laa.dstew.access.command";

  private EventTypes() {}

  /** Returns every {@code @Event} class in {@link #EVENTS_PACKAGE}. */
  public static Map<String, Class<?>> all() {
    return in(EVENTS_PACKAGE, new AnnotationMessageTypeResolver());
  }

  /**
   * Finds every {@code @Event} class under a package, keyed by the event name Axon stores.
   *
   * @throws IllegalStateException if two classes share a name
   */
  static Map<String, Class<?>> in(String basePackage, MessageTypeResolver resolver) {
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
