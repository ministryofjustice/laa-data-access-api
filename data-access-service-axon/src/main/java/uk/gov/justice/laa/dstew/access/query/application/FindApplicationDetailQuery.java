package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;

/** Finds an Application's hydrated detail and related read models by its internal identifier. */
public record FindApplicationDetailQuery(UUID applicationId, ReadAccessScope accessScope) {
  public FindApplicationDetailQuery(UUID applicationId) {
	this(applicationId, new UnrestrictedReadAccessScope());
  }
}
