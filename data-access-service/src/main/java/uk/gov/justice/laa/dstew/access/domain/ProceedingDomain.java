package uk.gov.justice.laa.dstew.access.domain;

import java.util.Map;
import java.util.UUID;

/** Domain record representing a proceeding. */
public record ProceedingDomain(
    UUID applicationId, boolean isLead, Map<String, Object> proceedingContent) {}
