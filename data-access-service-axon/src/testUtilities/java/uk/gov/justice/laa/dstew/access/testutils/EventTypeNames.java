package uk.gov.justice.laa.dstew.access.testutils;

import org.axonframework.messaging.eventhandling.annotation.Event;

/** Resolves the stored event type name, {@code namespace.name}, from an event's annotation. */
public final class EventTypeNames {

  private EventTypeNames() {}

  public static String of(Class<?> eventClass) {
    Event event = eventClass.getAnnotation(Event.class);
    if (event == null) {
      throw new IllegalArgumentException(eventClass.getName() + " has no @Event annotation");
    }
    return event.namespace() + "." + event.name();
  }
}
