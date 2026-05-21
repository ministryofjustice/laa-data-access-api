package uk.gov.justice.laa.dstew.access.utils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;

/** Utility class for extracting values from JSON strings. */
public class JsonUtils {

  /**
   * Extracts a string value for the given field name from a JSON string.
   *
   * <p>Returns {@code null} if the input is null, blank, or does not contain the requested field.
   *
   * @param json the raw JSON string to parse
   * @param fieldName the name of the field to extract
   * @return the extracted string value, or {@code null} if the field is absent or null
   * @throws IllegalArgumentException if the {@code json} cannot be parsed
   */
  public static String extractStringField(String json, String fieldName) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonNode node = MapperUtil.getObjectMapper().readTree(json);
      JsonNode fieldNode = node.get(fieldName);
      if (fieldNode == null || fieldNode.isNull()) {
        return null;
      }
      return fieldNode.stringValueOpt().orElse(null);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to parse JSON when extracting field '%s': %s", fieldName, e.getMessage()));
    }
  }
}
