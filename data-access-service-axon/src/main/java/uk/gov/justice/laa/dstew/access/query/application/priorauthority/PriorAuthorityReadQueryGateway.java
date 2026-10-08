package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.utils.security.AccessAwareQueryExecutor;

/** Repository-selection boundary for protected Prior Authority current-state reads. */
@Component
public class PriorAuthorityReadQueryGateway {
  private final PriorAuthorityReadRepository repository;
  private final AccessAwareQueryExecutor executor;

  public PriorAuthorityReadQueryGateway(
      PriorAuthorityReadRepository repository, AccessAwareQueryExecutor executor) {
    this.repository = repository;
    this.executor = executor;
  }

  public Optional<PriorAuthorityReadModel> findPriorAuthority(
      Specification<PriorAuthorityReadModel> functional,
      Specification<PriorAuthorityReadModel> access) {
    return executor.findOne(repository, functional, access);
  }
}
