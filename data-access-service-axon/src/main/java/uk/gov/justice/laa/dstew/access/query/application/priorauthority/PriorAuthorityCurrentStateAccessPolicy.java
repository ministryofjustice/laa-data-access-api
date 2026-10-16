package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessPolicy;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessScope;

/** Maps provider-office access scopes to the current Prior Authority projection. */
@Component
public class PriorAuthorityCurrentStateAccessPolicy
    implements ReadAccessPolicy<PriorAuthorityReadModel> {
  @Override
  public Specification<PriorAuthorityReadModel> restrictionFor(ReadAccessScope scope) {
    if (!(scope instanceof OfficeCodeReadAccessScope officeScope)) {
      return (root, query, cb) -> cb.conjunction();
    }
    if (officeScope.permittedOfficeCodes().isEmpty()) {
      return (root, query, cb) -> cb.disjunction();
    }
    return (root, query, cb) -> root.get("officeCode").in(officeScope.permittedOfficeCodes());
  }
}
