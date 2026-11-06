package uk.gov.justice.laa.dstew.access.query.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadRepository;
import uk.gov.justice.laa.dstew.access.query.utils.security.AccessAwareQueryExecutor;

/** Repository-selection boundary for protected Application read models. */
@Component
public class ApplicationReadQueryGateway {
  private final ApplicationReadRepository applicationReadRepository;
  private final ApplicationSummaryReadRepository summaryRepository;
  private final AccessAwareQueryExecutor executor;

  /**
   * creates the gateway wrapper around the repositories. Prevents use cases working around the
   * access restrictions.
   *
   * @param applicationReadRepository - application read model repository
   * @param summaryRepository - application list index read repository
   * @param executor - query executor that applies security filtering
   */
  public ApplicationReadQueryGateway(
      ApplicationReadRepository applicationReadRepository,
      ApplicationSummaryReadRepository summaryRepository,
      AccessAwareQueryExecutor executor) {
    this.applicationReadRepository = applicationReadRepository;
    this.summaryRepository = summaryRepository;
    this.executor = executor;
  }

  public Optional<ApplicationReadModel> findApplication(
      Specification<ApplicationReadModel> functional, Specification<ApplicationReadModel> access) {
    return executor.findOne(applicationReadRepository, functional, access);
  }

  public List<ApplicationReadModel> findApplications(
      Collection<java.util.UUID> ids, Specification<ApplicationReadModel> access) {
    return executor.findAll(
        applicationReadRepository, (root, query, cb) -> root.get("applicationId").in(ids), access);
  }

  public Page<ApplicationSummaryReadModel> findApplicationIndexPage(
      Specification<ApplicationSummaryReadModel> functional,
      Pageable pageable,
      Specification<ApplicationSummaryReadModel> access) {
    return executor.findAll(summaryRepository, functional, pageable, access);
  }
}
