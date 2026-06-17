package uk.gov.justice.laa.dstew.access.domain;

import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** Domain record representing a proceeding on a legal aid application. */
@Builder(toBuilder = true)
public record ProceedingDomain(
    UUID id,
    UUID applyProceedingId,
    String description,
    boolean isLead,
    Map<String, Object> proceedingContent,
    String createdBy,
    String updatedBy) {}
