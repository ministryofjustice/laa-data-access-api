package uk.gov.justice.laa.dstew.access.util;

import lombok.experimental.UtilityClass;

/** Maps enum values with matching names between enum types. */
@UtilityClass
public final class EnumMapping {

  /** Maps {@code source} to the value in {@code targetType} with the same name, preserving null. */
  public static <S extends Enum<S>, T extends Enum<T>> T map(S source, Class<T> targetType) {
    return source == null ? null : Enum.valueOf(targetType, source.name());
  }
}
