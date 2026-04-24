package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing an application. */
@Builder(toBuilder = true)
public record ApplicationDomain(
    UUID id,
    ApplicationStatus status,
    String laaReference,
    String officeCode,
    UUID applyApplicationId,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    Instant createdAt,
    String applicationContent,
    List<Individual> individuals,
    int schemaVersion,
    Long version,
    UUID caseworkerId,
    Boolean isAutoGranted,
    List<ProceedingDomain> proceedings,
    DecisionDomain decision) {}
