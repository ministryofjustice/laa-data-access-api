package uk.gov.justice.laa.dstew.access.config.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Deserialises an Integer only from a JSON integer token; rejects floats, strings and booleans. */
public class StrictIntegerDeserializer extends ValueDeserializer<Integer> {

  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) {
    if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
      return parser.getIntValue();
    }
    return (Integer) context.handleUnexpectedToken(Integer.class, parser);
  }
}
