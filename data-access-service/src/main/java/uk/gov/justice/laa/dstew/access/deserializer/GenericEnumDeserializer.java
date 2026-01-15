package uk.gov.justice.laa.dstew.access.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
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
  public E deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {

    String value = parser.getText();
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
