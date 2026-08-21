package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

/** Composite identity for an immutable version of prior-authority data. */
@Embeddable
public record PriorAuthorityDataId(
    @Column(name = "submission_id") UUID submissionId,
    @Column(name = "data_version") long dataVersion)
    implements Serializable {}
