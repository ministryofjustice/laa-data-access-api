package uk.gov.justice.laa.dstew.access.usecase.getapplication.model;

import java.util.UUID;
import lombok.Builder;

/** Domain read model for potential duplicate applications. */
@Builder(toBuilder = true)
public record PotentialDuplicateReadModel(
    UUID applicationId,
    String laaReference,
    String legacyReference) {}
