package uk.gov.justice.laa.dstew.access.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Utility for serialising generated request models to JSON with a consistent error wrapper. */
public final class RequestSerialiser {

  private RequestSerialiser() {}

  /**
   * Serialises the given request to JSON, wrapping any failure in an {@link IllegalStateException}.
   *
   * @param objectMapper the mapper to serialise with
   * @param request the request to serialise
   * @return the serialised JSON
   */
  public static String serialise(ObjectMapper objectMapper, Object request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "Unable to serialise %s".formatted(request.getClass().getSimpleName()), exception);
    }
  }
}
