package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;

/**
 * Maps between Instant and OffsetDateTime.
 */
@Mapper(componentModel = "spring")
public interface OffsetInstantMapper {
  /**
   * Converts a {@link Instant} timestamp to an {@link OffsetDateTime} using UTC as the default zone offset.
   *
   * @param instant the {@link Instant} to convert
   * @return the equivalent {@link OffsetDateTime}, or {@code null} if the input is null
   */
  default OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
