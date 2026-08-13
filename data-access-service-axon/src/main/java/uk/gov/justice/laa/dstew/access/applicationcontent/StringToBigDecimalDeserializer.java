package uk.gov.justice.laa.dstew.access.applicationcontent;

import java.math.BigDecimal;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Deserializes a JSON string token into a {@link BigDecimal}. Accepts {@code null} and valid
 * numeric strings. Rejects non-numeric strings with a {@link MismatchedInputException}.
 */
public class StringToBigDecimalDeserializer extends StdDeserializer<BigDecimal> {

  public StringToBigDecimalDeserializer() {
    super(BigDecimal.class);
  }

  @Override
  public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) {
    if (p.currentToken() == JsonToken.VALUE_NULL) {
      return null;
    }
    String text = p.getText();
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw MismatchedInputException.from(
          p, BigDecimal.class, "Invalid value '%s' — expected a numeric string".formatted(text));
    }
  }

  @Override
  public BigDecimal getNullValue(DeserializationContext ctxt) {
    return null;
  }
}
