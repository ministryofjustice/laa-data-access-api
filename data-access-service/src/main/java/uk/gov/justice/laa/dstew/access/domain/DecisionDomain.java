package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import lombok.Builder;

/** Domain record representing the overall decision on an application. */
@Builder(toBuilder = true)
public record DecisionDomain(String overallDecision, Instant modifiedAt) {}
