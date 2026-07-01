package uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.infrastructure.AssignCaseworkerCaseworkerGateway;

/** JPA implementation of {@link AssignCaseworkerCaseworkerGateway}. */
public class AssignCaseworkerCaseworkerJpaGateway implements AssignCaseworkerCaseworkerGateway {

  private final CaseworkerRepository caseworkerRepository;

  public AssignCaseworkerCaseworkerJpaGateway(CaseworkerRepository caseworkerRepository) {
    this.caseworkerRepository = caseworkerRepository;
  }

  @Override
  public boolean exists(UUID caseworkerId) {
    return caseworkerRepository.existsById(caseworkerId);
  }
}
