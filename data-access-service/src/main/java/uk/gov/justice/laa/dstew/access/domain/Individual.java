package uk.gov.justice.laa.dstew.access.domain;

import java.time.LocalDate;
import java.util.Map;
import lombok.Builder;

/** Domain record representing an individual on an application. */
@Builder(toBuilder = true)
public record Individual(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Map<String, Object> details,
    String type) {}
