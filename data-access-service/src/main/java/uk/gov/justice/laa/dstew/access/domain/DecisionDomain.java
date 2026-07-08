package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.domain.enums.DecisionStatus;

/** Domain record representing the overall decision on an application. */
@Builder(toBuilder = true)
public record DecisionDomain(DecisionStatus overallDecision, Instant modifiedAt) {}
