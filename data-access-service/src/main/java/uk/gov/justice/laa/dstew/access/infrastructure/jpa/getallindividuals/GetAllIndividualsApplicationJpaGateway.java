package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallindividuals;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure.GetAllIndividualsApplicationGateway;

/** JPA implementation of {@link GetAllIndividualsApplicationGateway}. */
public class GetAllIndividualsApplicationJpaGateway implements GetAllIndividualsApplicationGateway {

  private final ApplicationRepository applicationRepository;
  private final GetAllIndividualsGatewayMapper gatewayMapper;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository the JPA repository
   * @param gatewayMapper the gateway mapper
   */
  public GetAllIndividualsApplicationJpaGateway(
      ApplicationRepository applicationRepository, GetAllIndividualsGatewayMapper gatewayMapper) {
    this.applicationRepository = applicationRepository;
    this.gatewayMapper = gatewayMapper;
  }

  @Override
  public ApplicationClientDetailsDomain findClientDetails(UUID applicationId) {
    return applicationRepository
        .findById(applicationId)
        .map(gatewayMapper::toClientDetails)
        .orElseThrow();
  }
}
