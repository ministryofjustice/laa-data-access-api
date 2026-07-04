package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing a legal aid application. Pure Java — no JPA, no Spring imports. */
@Builder(toBuilder = true)
public record ApplicationDomain(
    UUID id,
    Long version,
    String status,
    String laaReference,
    String officeCode,
    Map<String, Object> applicationContent,
    Set<IndividualDomain> individuals,
    Integer schemaVersion,
    Instant createdAt,
    Instant modifiedAt,
    UUID applyApplicationId,
    Instant submittedAt,
    Boolean usedDelegatedFunctions,
    String categoryOfLaw,
    String matterType,
    Boolean isAutoGranted,
    Set<ProceedingDomain> proceedings,
    UUID caseworkerId,
    DecisionDomain decision) {}
