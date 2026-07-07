package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.controller.application.GetAllApplicationsQueryMapper;
import uk.gov.justice.laa.dstew.access.controller.application.GetAllApplicationsResponseMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications.GetAllApplicationsApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications.GetAllApplicationsCaseworkerJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications.GetAllApplicationsGatewayMapper;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsUseCase;

/** Spring configuration that wires all beans for the getAllApplications use case. */
@Configuration
@RequiredArgsConstructor
public class GetAllApplicationsConfig {

  private final ApplicationRepository applicationRepository;
  private final CaseworkerRepository caseworkerRepository;

  /** Creates the {@link GetAllApplicationsGatewayMapper} bean. */
  @Bean
  public GetAllApplicationsGatewayMapper getAllApplicationsGatewayMapper() {
    return new GetAllApplicationsGatewayMapper();
  }

  /** Creates the {@link GetAllApplicationsApplicationJpaGateway} bean. */
  @Bean
  public GetAllApplicationsApplicationJpaGateway getAllApplicationsApplicationGateway(
      GetAllApplicationsGatewayMapper gatewayMapper) {
    return new GetAllApplicationsApplicationJpaGateway(applicationRepository, gatewayMapper);
  }

  /** Creates the {@link GetAllApplicationsCaseworkerJpaGateway} bean. */
  @Bean
  public GetAllApplicationsCaseworkerJpaGateway getAllApplicationsCaseworkerGateway() {
    return new GetAllApplicationsCaseworkerJpaGateway(caseworkerRepository);
  }

  /**
   * Creates the use-case bean.
   *
   * @param appGateway the application gateway
   * @param caseworkerGateway the caseworker gateway
   * @return the use case
   */
  @Bean
  public GetAllApplicationsUseCase getAllApplicationsUseCase(
      GetAllApplicationsApplicationJpaGateway appGateway,
      GetAllApplicationsCaseworkerJpaGateway caseworkerGateway) {
    return new GetAllApplicationsUseCase(appGateway, caseworkerGateway);
  }

  /** Creates the {@link GetAllApplicationsQueryMapper} bean. */
  @Bean
  public GetAllApplicationsQueryMapper getAllApplicationsQueryMapper() {
    return new GetAllApplicationsQueryMapper();
  }

  /** Creates the {@link GetAllApplicationsResponseMapper} bean. */
  @Bean
  public GetAllApplicationsResponseMapper getAllApplicationsResponseMapper() {
    return new GetAllApplicationsResponseMapper();
  }
}
