package uk.gov.justice.laa.dstew.access.domain;

import java.util.UUID;
import lombok.Builder;

/** Domain record representing a proceeding. */
@Builder(toBuilder = true)
public record ProceedingDomain(
    UUID id,
    UUID applyProceedingId,
    String description,
    boolean isLead,
    String proceedingContent,
    MeritsDecisionDomain meritsDecision) {}
