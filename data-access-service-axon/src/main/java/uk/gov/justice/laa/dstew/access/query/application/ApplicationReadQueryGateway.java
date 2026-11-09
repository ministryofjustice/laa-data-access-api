package uk.gov.justice.laa.dstew.access.query.application;

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
  private final ApplicationSummaryReadRepository summaryRepository;
  private final AccessAwareQueryExecutor executor;

  /**
   * creates the gateway wrapper around the repositories. Prevents use cases working around the
   * access restrictions.
   *
   * @param summaryRepository - application summary repository
   * @param executor - query executor that applies security filtering
   */
  public ApplicationReadQueryGateway(
      ApplicationSummaryReadRepository summaryRepository, AccessAwareQueryExecutor executor) {
    this.summaryRepository = summaryRepository;
    this.executor = executor;
  }

  public Page<ApplicationSummaryReadModel> findApplicationIndexPage(
      Specification<ApplicationSummaryReadModel> functional,
      Pageable pageable,
      Specification<ApplicationSummaryReadModel> access) {
    return executor.findAll(summaryRepository, functional, pageable, access);
  }
}
