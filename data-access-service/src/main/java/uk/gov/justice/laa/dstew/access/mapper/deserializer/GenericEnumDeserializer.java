package uk.gov.justice.laa.dstew.access.mapper.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.util.Arrays;

/**
 * Generic deserializer for Enum types.
 *
 * @param <E> the enum type
 */
public class GenericEnumDeserializer<E extends Enum> extends JsonDeserializer<E> {

  private final Class<E> enumType;

  public GenericEnumDeserializer(Class<E> enumType) {
    this.enumType = enumType;
  }

  @Override
  public E deserialize(JsonParser p,
                       DeserializationContext ctxt)
      throws java.io.IOException {
    String value = p.getText();
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      // Use case-insensitive matching to find the corresponding enum constant
      return Arrays.stream(enumType.getEnumConstants())
          .filter(e -> e.name().equalsIgnoreCase(value.trim()))
          .findFirst()
          .orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
