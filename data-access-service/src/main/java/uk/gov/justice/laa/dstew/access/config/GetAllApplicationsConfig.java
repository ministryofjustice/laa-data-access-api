package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.controller.application.GetAllApplicationsCommandMapper;
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

  /**
   * Creates the gateway mapper bean.
   *
   * @return the gateway mapper
   */
  @Bean
  public GetAllApplicationsGatewayMapper getAllApplicationsGatewayMapper() {
    return new GetAllApplicationsGatewayMapper();
  }

  /**
   * Creates the application JPA gateway bean.
   *
   * @param gatewayMapper the gateway mapper
   * @return the application JPA gateway
   */
  @Bean
  public GetAllApplicationsApplicationJpaGateway getAllApplicationsApplicationGateway(
      GetAllApplicationsGatewayMapper gatewayMapper) {
    return new GetAllApplicationsApplicationJpaGateway(applicationRepository, gatewayMapper);
  }

  /**
   * Creates the caseworker JPA gateway bean.
   *
   * @return the caseworker JPA gateway
   */
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

  /**
   * Creates the command mapper bean.
   *
   * @return the command mapper
   */
  @Bean
  public GetAllApplicationsCommandMapper getAllApplicationsCommandMapper() {
    return new GetAllApplicationsCommandMapper();
  }

  /**
   * Creates the response mapper bean.
   *
   * @return the response mapper
   */
  @Bean
  public GetAllApplicationsResponseMapper getAllApplicationsResponseMapper() {
    return new GetAllApplicationsResponseMapper();
  }
}
