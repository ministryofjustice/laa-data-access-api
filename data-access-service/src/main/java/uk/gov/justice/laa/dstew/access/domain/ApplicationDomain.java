package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Domain record representing an application. */
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
    Map<String, Object> applicationContent,
    List<Individual> individuals,
    int schemaVersion) {}
