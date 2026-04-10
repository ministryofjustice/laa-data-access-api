package uk.gov.justice.laa.dstew.access.deserializer;

import java.util.Arrays;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Generic deserializer for Enum types.
 *
 * @param <E> the enum type
 */
public class GenericEnumDeserializer<E extends Enum> extends ValueDeserializer<E> {

  private final Class<E> enumType;

  public GenericEnumDeserializer(Class<E> enumType) {
    this.enumType = enumType;
  }

  @Override
  public E deserialize(JsonParser parser, DeserializationContext ctxt) {

    String value = parser.getString();
    if (value == null || value.isEmpty()) {
      return null;
    }

    return Arrays.stream(enumType.getEnumConstants())
        .filter(enumConstant -> enumConstant.name().equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElse(null);
  }
}
