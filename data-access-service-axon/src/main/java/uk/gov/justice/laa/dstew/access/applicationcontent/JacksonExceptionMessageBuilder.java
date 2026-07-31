package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Utility for building human-readable validation error messages from Jackson deserialization
 * exceptions, including precise field location (with array indices).
 */
public class JacksonExceptionMessageBuilder {

  private JacksonExceptionMessageBuilder() {}

  /**
   * Derives a detailed error message from a {@link JacksonException}. When the exception is a
   * {@link MismatchedInputException}, the full field path (e.g. {@code
   * proceedings[0].substantiveCostLimitation}) is included.
   *
   * @param ex the Jackson exception to describe
   * @return a human-readable error message
   */
  public static @NonNull String buildMessage(@NonNull JacksonException ex) {
    if (ex instanceof MismatchedInputException mie) {
      return buildMessageForMismatch(mie);
    }
    return ex.getOriginalMessage();
  }

  /**
   * Derives a detailed error message from an {@link IllegalArgumentException} whose cause is a
   * {@link MismatchedInputException} (e.g. OpenAPI-generated enum deserialization failures).
   *
   * @param iae the {@link IllegalArgumentException} thrown during deserialization
   * @param mie the {@link MismatchedInputException} that wraps it
   * @return a human-readable error message including valid enum values
   */
  public static @NonNull String buildMessageForInvalidEnum(
      @NonNull IllegalArgumentException iae, @NonNull MismatchedInputException mie) {
    Class<?> enumClass = mie.getTargetType();
    if (enumClass == null || !enumClass.isEnum()) {
      return iae.getLocalizedMessage();
    }
    String validValues =
        Arrays.stream(enumClass.getEnumConstants())
            .map(e -> ((Enum<?>) e).name())
            .collect(Collectors.joining(", "));
    return "%s. Valid values are: %s".formatted(iae.getLocalizedMessage(), validValues);
  }

  private static @NonNull String buildMessageForMismatch(@NonNull MismatchedInputException mie) {
    String field = buildFieldPath(mie);
    Class<?> targetType = mie.getTargetType();
    Class<?> classToCheck = targetType != null ? targetType : Object.class;
    String expectedType =
        !classToCheck.isEnum()
            ? classToCheck.getSimpleName()
            : List.of(classToCheck.getEnumConstants()).toString();
    return "Invalid data type for field '%s'. Expected: %s.".formatted(field, expectedType);
  }

  /**
   * Builds a dotted field path from the Jackson exception's path references, including bracket
   * notation for array indices (e.g. {@code proceedings[0].substantiveCostLimitation}).
   */
  public static @NonNull String buildFieldPath(@NonNull MismatchedInputException mie) {
    StringBuilder sb = new StringBuilder();
    for (var ref : mie.getPath()) {
      String propertyName = ref.getPropertyName();
      int index = ref.getIndex();
      if (propertyName != null) {
        if (!sb.isEmpty()) {
          sb.append(".");
        }
        sb.append(propertyName);
      } else if (index >= 0) {
        sb.append("[").append(index).append("]");
      }
    }
    return sb.isEmpty() ? "unknown" : sb.toString();
  }
}
