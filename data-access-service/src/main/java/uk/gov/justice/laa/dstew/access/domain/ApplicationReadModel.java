package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Domain read model returned by the get-application use case. */
@Builder(toBuilder = true)
public record ApplicationReadModel(
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
    String applicationType,
    Long version,
    List<OpponentReadModel> opponents,
    ProviderReadModel provider,
    List<ApplicationProceedingReadModel> proceedings) {}
