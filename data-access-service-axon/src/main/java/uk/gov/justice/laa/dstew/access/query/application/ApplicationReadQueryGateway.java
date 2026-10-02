package uk.gov.justice.laa.dstew.access.query.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.utils.security.AccessAwareQueryExecutor;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadRepository;

/** Repository-selection boundary for protected Application read models. */
@Component
public class ApplicationReadQueryGateway {
  private final ApplicationReadRepository applicationReadRepository;
  private final ApplicationListIndexReadRepository listIndexRepository;
  private final AccessAwareQueryExecutor executor;

  public ApplicationReadQueryGateway(
      ApplicationReadRepository applicationReadRepository,
      ApplicationListIndexReadRepository listIndexRepository,
      AccessAwareQueryExecutor executor) {
    this.applicationReadRepository = applicationReadRepository;
    this.listIndexRepository = listIndexRepository;
    this.executor = executor;
  }

  public Optional<ApplicationReadModel> findApplication(
      Specification<ApplicationReadModel> functional, Specification<ApplicationReadModel> access) {
    return executor.findOne(applicationReadRepository, functional, access);
  }

  public List<ApplicationReadModel> findApplications(
      Collection<java.util.UUID> ids, Specification<ApplicationReadModel> access) {
    return executor.findAll(applicationReadRepository, (root, query, cb) -> root.get("applicationId").in(ids), access);
  }

  public Page<ApplicationListIndexReadModel> findApplicationIndexPage(
      Specification<ApplicationListIndexReadModel> functional,
      Pageable pageable,
      Specification<ApplicationListIndexReadModel> access) {
    return executor.findAll(listIndexRepository, functional, pageable, access);
  }
}

