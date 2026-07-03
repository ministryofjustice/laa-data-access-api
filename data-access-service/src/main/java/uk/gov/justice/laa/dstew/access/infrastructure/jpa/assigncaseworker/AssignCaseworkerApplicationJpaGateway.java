package uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure.AssignCaseworkerApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.model.AssignCaseworkerApplication;

/** JPA implementation of {@link AssignCaseworkerApplicationGateway}. */
public class AssignCaseworkerApplicationJpaGateway implements AssignCaseworkerApplicationGateway {

  private final ApplicationRepository applicationRepository;
  private final CaseworkerRepository caseworkerRepository;
  private final AssignCaseworkerGatewayMapper gatewayMapper;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository the Spring Data application repository
   * @param caseworkerRepository the Spring Data caseworker repository
   * @param gatewayMapper the mapper for this use case
   */
  public AssignCaseworkerApplicationJpaGateway(
      ApplicationRepository applicationRepository,
      CaseworkerRepository caseworkerRepository,
      AssignCaseworkerGatewayMapper gatewayMapper) {
    this.applicationRepository = applicationRepository;
    this.caseworkerRepository = caseworkerRepository;
    this.gatewayMapper = gatewayMapper;
  }

  @Override
  public List<AssignCaseworkerApplication> findAllByIds(List<UUID> ids) {
    return applicationRepository.findAllById(ids).stream().map(gatewayMapper::toReadModel).toList();
  }

  /**
   * Persists the caseworker assignment for all supplied applications. Resolves the caseworker
   * entity once, then loads each managed application entity before applying changes to preserve the
   * {@code @Version} field.
   *
   * @param applications the applications to update
   * @param caseworkerId the ID of the caseworker to assign
   */
  @Override
  public void saveAll(List<AssignCaseworkerApplication> applications, UUID caseworkerId) {
    CaseworkerEntity caseworker =
        caseworkerRepository
            .findById(caseworkerId)
            .orElseThrow(() -> new NoSuchElementException("Caseworker not found: " + caseworkerId));
    for (AssignCaseworkerApplication app : applications) {
      ApplicationEntity entity =
          applicationRepository
              .findById(app.id())
              .orElseThrow(() -> new NoSuchElementException("Application not found: " + app.id()));
      entity.setCaseworker(caseworker);
      entity.setModifiedAt(Instant.now());
      applicationRepository.save(entity);
    }
  }
}
