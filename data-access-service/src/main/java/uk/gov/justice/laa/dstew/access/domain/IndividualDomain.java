package uk.gov.justice.laa.dstew.access.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing an individual on a legal aid application. */
@Builder(toBuilder = true)
public record IndividualDomain(
    UUID id,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Map<String, Object> individualContent,
    String type) {}
