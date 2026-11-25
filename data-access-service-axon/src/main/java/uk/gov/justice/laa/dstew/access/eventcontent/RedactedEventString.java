package uk.gov.justice.laa.dstew.access.eventcontent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Renders an event record without its content, so that logging an event cannot reveal it.
 * Components marked {@link RetainedField} show their values; a content component shows {@code
 * <content>}, or {@code null} when it is absent or erased.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedactedEventString {

  /** Returns {@code Type[name=value, ...]} with every present content value hidden. */
  public static String of(Record event) {
    return Arrays.stream(event.getClass().getRecordComponents())
        .map(component -> component.getName() + "=" + render(event, component))
        .collect(Collectors.joining(", ", event.getClass().getSimpleName() + "[", "]"));
  }

  private static String render(Record event, RecordComponent component) {
    Object value;
    try {
      value = component.getAccessor().invoke(event);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Cannot read " + component.getName(), e);
    }
    if (value == null || component.isAnnotationPresent(RetainedField.class)) {
      return String.valueOf(value);
    }
    return "<content>";
  }
}
