package uk.gov.justice.laa.dstew.access.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Domain record for a proceeding within the get-application read model. */
@Builder(toBuilder = true)
public record ApplicationProceedingDomain(
    UUID proceedingId,
    String description,
    String proceedingType,
    String categoryOfLaw,
    String matterType,
    String levelOfService,
    Double substantiveCostLimitation,
    LocalDate delegatedFunctionsDate,
    String meritsDecision,
    List<InvolvedChildDomain> involvedChildren,
    List<ScopeLimitationDomain> scopeLimitations) {}
