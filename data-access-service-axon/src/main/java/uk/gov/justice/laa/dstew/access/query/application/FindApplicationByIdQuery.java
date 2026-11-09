package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;

/** Finds an Application by its internal identifier, read from its aggregate. */
public record FindApplicationByIdQuery(UUID applicationId, ReadAccessScope accessScope) {
  public FindApplicationByIdQuery(UUID applicationId) {
    this(applicationId, new UnrestrictedReadAccessScope());
  }
}
