package uk.gov.justice.laa.dstew.access.query.utils.security;

import java.util.Set;

/** Restricts a read to the supplied, already-authorised provider office codes. */
public record OfficeCodeReadAccessScope(Set<String> permittedOfficeCodes)
    implements ReadAccessScope {
  public OfficeCodeReadAccessScope {
    permittedOfficeCodes = Set.copyOf(permittedOfficeCodes);
  }
}
