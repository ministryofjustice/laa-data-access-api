package uk.gov.justice.laa.dstew.access.eventcontent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Event content that may be erased. Erased is a real state, not a null: reading an erased value
 * throws, so every use of content is visible. JSON carries only the value; an absent or null value
 * reads as erased.
 */
public final class Erasable<T> {

  private static final Erasable<?> ERASED = new Erasable<>(null);

  private final @Nullable T value;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  Erasable(@Nullable T value) {
    this.value = value;
  }

  /** Wraps present content. A null value is treated as erased. */
  public static <T> Erasable<T> of(@Nullable T value) {
    return value == null ? erased() : new Erasable<>(value);
  }

  @SuppressWarnings("unchecked")
  public static <T> Erasable<T> erased() {
    return (Erasable<T>) ERASED;
  }

  /** Normalises a missing wrapper, as left by deserialisation of erased content, to erased. */
  public static <T> Erasable<T> orErased(@Nullable Erasable<T> erasable) {
    return erasable == null ? erased() : erasable;
  }

  public boolean isErased() {
    return value == null;
  }

  /** Returns the content, or throws {@link ContentErasedException} if it has been erased. */
  public T value() {
    if (value == null) {
      throw new ContentErasedException();
    }
    return value;
  }

  /** Returns the content, or {@code fallback} if it has been erased. */
  public @Nullable T orElse(@Nullable T fallback) {
    return value == null ? fallback : value;
  }

  /** Maps present content; erased stays erased. */
  public <R> Erasable<R> map(Function<? super T, ? extends R> mapper) {
    return value == null ? erased() : of(mapper.apply(value));
  }

  @JsonValue
  @Nullable T jsonValue() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Erasable<?> that && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return isErased() ? "Erasable[erased]" : "Erasable[present]";
  }
}
