package uk.gov.justice.laa.dstew.access.domain;

import java.time.LocalDate;
import java.util.Map;

/** Domain record representing an individual on an application. */
public record Individual(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Map<String, Object> details,
    String type) {}
