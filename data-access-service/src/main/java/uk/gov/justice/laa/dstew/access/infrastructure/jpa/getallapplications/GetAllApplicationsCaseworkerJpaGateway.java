package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure.GetAllApplicationsCaseworkerGateway;

/**
 * JPA gateway implementation for caseworker existence checks in the getAllApplications use case.
 */
public class GetAllApplicationsCaseworkerJpaGateway implements GetAllApplicationsCaseworkerGateway {

  private final CaseworkerRepository caseworkerRepository;

  /**
   * Constructs the gateway with the required repository.
   *
   * @param caseworkerRepository the repository for caseworker queries
   */
  public GetAllApplicationsCaseworkerJpaGateway(CaseworkerRepository caseworkerRepository) {
    this.caseworkerRepository = caseworkerRepository;
  }

  @Override
  public boolean caseworkerExists(UUID userId) {
    return caseworkerRepository.countById(userId) > 0L;
  }
}
