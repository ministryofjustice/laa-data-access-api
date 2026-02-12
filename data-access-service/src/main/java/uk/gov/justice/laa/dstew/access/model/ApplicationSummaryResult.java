package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Record representing a summary view of an application, containing selected extracted details.
 */
@Builder
public record ApplicationSummaryResult(
    UUID id,
    String laaReference,
    ApplicationStatus status,
    Instant submittedAt,
    Boolean isAutoGranted,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Boolean usedDelegatedFunctions,
    String officeCode,
    UUID caseworkerId,
    String clientFirstName,
    String clientLastName,
    LocalDate clientDateOfBirth,
    Instant modifiedAt
) {}