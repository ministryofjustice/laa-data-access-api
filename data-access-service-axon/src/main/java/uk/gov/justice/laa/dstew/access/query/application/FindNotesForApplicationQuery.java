package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;

/** Retrieves all notes for an Application by its internal identifier. */
public record FindNotesForApplicationQuery(UUID applicationId, ReadAccessScope accessScope) {
  public FindNotesForApplicationQuery(UUID applicationId) {
    this(applicationId, new UnrestrictedReadAccessScope());
  }
}
