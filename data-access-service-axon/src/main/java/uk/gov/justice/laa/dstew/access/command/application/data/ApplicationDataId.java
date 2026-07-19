package uk.gov.justice.laa.dstew.access.command.application.data;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

/** Composite identity for an immutable version of application data. */
@Embeddable
public record ApplicationDataId(UUID applicationId, long version) implements Serializable {}
