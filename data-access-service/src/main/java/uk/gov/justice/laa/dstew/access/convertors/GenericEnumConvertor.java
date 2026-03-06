package uk.gov.justice.laa.dstew.access.convertors;

import java.util.Arrays;

/**
 * Generic deserializer for Enum types.
 *
 * @param <E> the enum type
 */
public class GenericEnumConvertor<E extends Enum> {

  private final Class<E> enumType;

  public GenericEnumConvertor(Class<E> enumType) {
    this.enumType = enumType;
  }

  /**
   * Converts a string value to the corresponding enum constant, ignoring case and trimming whitespace.
   *
   * @param value the string value to convert
   * @return the corresponding enum constant, or null if no match is found
   */
  public E lenientEnumConversion(String value) {

    if (value == null || value.isEmpty()) {
      return null;
    }

    return Arrays.stream(enumType.getEnumConstants())
        .filter(enumConstant -> enumConstant
            .name()
            .equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElse(null);

  }
}
