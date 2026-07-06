package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Domain record representing a summary of a single legal aid application. Pure Java — no JPA, no
 * Spring, no API model imports.
 */
@Builder(toBuilder = true)
public record ApplicationSummaryDomain(
    UUID id,
    Instant submittedAt,
    Boolean isAutoGranted,
    String categoryOfLaw,
    String matterType,
    Boolean usedDelegatedFunctions,
    String laaReference,
    String officeCode,
    String status,
    UUID caseworkerId,
    String clientFirstName,
    String clientLastName,
    LocalDate clientDateOfBirth,
    String applicationType,
    Instant modifiedAt,
    Boolean isLead,
    List<LinkedApplicationSummaryDomain> linkedApplications) {}
