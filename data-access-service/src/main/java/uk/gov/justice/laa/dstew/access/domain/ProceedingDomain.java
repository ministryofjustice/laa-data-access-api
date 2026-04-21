package uk.gov.justice.laa.dstew.access.domain;

import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing a proceeding. */
@Builder(toBuilder = true)
public record ProceedingDomain(
    UUID applicationId, boolean isLead, Map<String, Object> proceedingContent) {}
