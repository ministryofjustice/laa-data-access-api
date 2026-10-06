package uk.gov.justice.laa.dstew.access.query.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadRepository;
import uk.gov.justice.laa.dstew.access.query.utils.security.AccessAwareQueryExecutor;

/** Repository-selection boundary for protected Application read models. */
@Component
public class ApplicationReadQueryGateway {
  private final ApplicationReadRepository applicationReadRepository;
  private final ApplicationListIndexReadRepository listIndexRepository;
  private final AccessAwareQueryExecutor executor;

  /**
   * creates the gateway wrapper around the repositories. Prevents use cases working around the
   * access restrictions.
   *
   * @param applicationReadRepository - application read model repository
   * @param listIndexRepository - application list index read repository
   * @param executor - query executor that applies security filtering
   */
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

  public List<ApplicationReadModel> findAllApplications(
      Specification<ApplicationReadModel> functional, Specification<ApplicationReadModel> access) {
    return executor.findAll(applicationReadRepository, functional, access);
  }

  public List<ApplicationReadModel> findApplications(
      Collection<java.util.UUID> ids, Specification<ApplicationReadModel> access) {
    return executor.findAll(
        applicationReadRepository, (root, query, cb) -> root.get("applicationId").in(ids), access);
  }

  public Page<ApplicationListIndexReadModel> findApplicationIndexPage(
      Specification<ApplicationListIndexReadModel> functional,
      Pageable pageable,
      Specification<ApplicationListIndexReadModel> access) {
    return executor.findAll(listIndexRepository, functional, pageable, access);
  }
}
