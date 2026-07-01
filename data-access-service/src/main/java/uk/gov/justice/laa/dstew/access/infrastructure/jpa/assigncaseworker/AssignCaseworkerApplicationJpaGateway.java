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
   * Persists the updated caseworker assignment for an existing application. Loads the managed
   * entity from the repository before applying changes to preserve the {@code @Version} field.
   *
   * @param caseworkerAssignment the caseworker assignment carrying the updated caseworker ID
   */
  @Override
  public void save(AssignCaseworkerApplication caseworkerAssignment) {
    ApplicationEntity entity =
        applicationRepository
            .findById(caseworkerAssignment.id())
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Application not found: " + caseworkerAssignment.id()));
    CaseworkerEntity caseworker =
        caseworkerRepository
            .findById(caseworkerAssignment.caseworkerId())
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Caseworker not found: " + caseworkerAssignment.caseworkerId()));
    entity.setCaseworker(caseworker);
    entity.setModifiedAt(Instant.now());
    applicationRepository.save(entity);
  }
}
