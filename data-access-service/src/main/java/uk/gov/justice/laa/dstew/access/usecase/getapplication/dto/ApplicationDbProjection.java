package uk.gov.justice.laa.dstew.access.usecase.getapplication.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.model.OpponentDetails;

/** Raw projection of an application entity and its parsed JSON content. No business logic. */
@Builder(toBuilder = true)
public record ApplicationDbProjection(
    UUID id,
    String status,
    String laaReference,
    Instant updatedAt,
    UUID caseworkerId,
    Instant submittedAt,
    Boolean isLead,
    Boolean usedDelegatedFunctions,
    Boolean autoGrant,
    String decisionStatus,
    Long version,
    String officeCode,
    String submitterEmail,
    List<OpponentDetails> opponents,
    List<ProceedingDbProjection> proceedings) {}
