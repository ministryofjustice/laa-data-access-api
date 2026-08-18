package uk.gov.justice.laa.dstew.access.infrastructure.jpa.unassigncaseworker;

import java.time.Instant;
import java.util.NoSuchElementException;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.infrastructure.UnassignCaseworkerApplicationGateway;

/** JPA implementation of {@link UnassignCaseworkerApplicationGateway}. */
public class UnassignCaseworkerApplicationJpaGateway
    implements UnassignCaseworkerApplicationGateway {

  private final ApplicationRepository applicationRepository;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository the Spring Data application repository
   * @param gatewayMapper the mapper for this use case
   */
  public UnassignCaseworkerApplicationJpaGateway(
      ApplicationRepository applicationRepository, UnassignCaseworkerGatewayMapper gatewayMapper) {
    this.applicationRepository = applicationRepository;
  }

  /**
   * Loads the managed entity from the repository before applying changes so the {@code @Version}
   * field is preserved and no {@code OptimisticLockException} is triggered.
   *
   * @param applicationDomain the updated domain record
   */
  @Override
  public void saveApplication(ApplicationDomain applicationDomain) {
    ApplicationEntity entity =
        applicationRepository
            .findById(applicationDomain.id())
            .orElseThrow(
                () ->
                    new NoSuchElementException("Application not found: " + applicationDomain.id()));
    entity.setCaseworker(null);
    entity.setModifiedAt(Instant.now());
    applicationRepository.save(entity);
  }
}
