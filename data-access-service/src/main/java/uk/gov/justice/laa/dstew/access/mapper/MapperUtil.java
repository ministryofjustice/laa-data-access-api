package uk.gov.justice.laa.dstew.access.mapper;

import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Utility class for configuring and providing a Jackson ObjectMapper instance. */
@ExcludeFromGeneratedCodeCoverage
public class MapperUtil {

  /**
   * Creates and configures a Jackson ObjectMapper.
   *
   * @return a configured ObjectMapper instance
   */
  public static @NonNull ObjectMapper getObjectMapper() {
    return new ObjectMapper();
  }
}
