package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getapplication;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.infrastructure.GetApplicationApplicationGateway;

/** JPA gateway for the get-application use case. */
public class GetApplicationApplicationJpaGateway implements GetApplicationApplicationGateway {

  private final ApplicationRepository applicationRepository;
  private final GetApplicationGatewayMapper getApplicationGatewayMapper;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository application repository
   * @param getApplicationGatewayMapper gateway mapper
   */
  public GetApplicationApplicationJpaGateway(
      ApplicationRepository applicationRepository,
      GetApplicationGatewayMapper getApplicationGatewayMapper) {
    this.applicationRepository = applicationRepository;
    this.getApplicationGatewayMapper = getApplicationGatewayMapper;
  }

  @Override
  public Optional<ApplicationReadModel> findApplicationReadModelById(UUID id) {
    return applicationRepository
        .findById(id)
        .map(getApplicationGatewayMapper::toApplicationReadModel);
  }
}
