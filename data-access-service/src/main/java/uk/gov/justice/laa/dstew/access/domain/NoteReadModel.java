package uk.gov.justice.laa.dstew.access.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Domain read model returned by the get-all-notes-for-application use case. */
@Builder(toBuilder = true)
public record NoteReadModel(
    UUID applicationId, String notes, Instant createdAt, String createdBy) {}
