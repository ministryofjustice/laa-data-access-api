package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing a decision. */
@Builder(toBuilder = true)
public record DecisionDomain(
    UUID id,
    OverallDecisionStatus overallDecision,
    Set<MeritsDecisionDomain> meritsDecisions,
    Instant modifiedAt) {}
