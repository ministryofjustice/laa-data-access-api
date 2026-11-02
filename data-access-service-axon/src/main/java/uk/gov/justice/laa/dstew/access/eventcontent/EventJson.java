package uk.gov.justice.laa.dstew.access.eventcontent;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one JSON mapper for event payloads and event content, owned by us rather than by Axon or
 * Spring. Decimals stay exact through JSON trees, which splitting and rejoining rely on. Anything
 * that parses event payloads or content must use this mapper.
 */
public final class EventJson {

  private final ObjectMapper mapper =
      JsonMapper.builder()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
          .build();

  public ObjectMapper mapper() {
    return mapper;
  }
}
