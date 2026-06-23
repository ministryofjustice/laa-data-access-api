package uk.gov.justice.laa.dstew.access.usecase.getapplication.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.InvolvedChild;

/** Raw projection of a proceeding entity and its parsed JSON content. No business logic. */
@Builder(toBuilder = true)
public record ProceedingDbProjection(
    UUID proceedingId,
    String description,
    String meritsDecision,
    String proceedingType,
    String categoryOfLaw,
    String matterType,
    String levelOfService,
    Double substantiveCostLimitation,
    LocalDate delegatedFunctionsDate,
    List<Map<String, Object>> scopeLimitations,
    List<InvolvedChild> involvedChildren) {}
