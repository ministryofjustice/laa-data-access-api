package uk.gov.justice.laa.dstew.access.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Read-model record for a single proceeding in the get-application response. */
@Builder(toBuilder = true)
public record ApplicationProceedingReadModel(
    UUID proceedingId,
    String description,
    String proceedingType,
    String categoryOfLaw,
    String matterType,
    String levelOfService,
    Double substantiveCostLimitation,
    LocalDate delegatedFunctionsDate,
    String meritsDecision,
    List<InvolvedChildReadModel> involvedChildren,
    List<ScopeLimitationReadModel> scopeLimitations) {}
