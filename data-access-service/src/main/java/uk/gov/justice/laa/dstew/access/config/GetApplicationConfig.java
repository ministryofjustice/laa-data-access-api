package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.controller.application.GetApplicationResponseMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getapplication.GetApplicationApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getapplication.GetApplicationGatewayMapper;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.GetApplicationReadModelMapper;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.GetApplicationUseCase;

/** Spring configuration for the get-application use case. */
@Configuration
@RequiredArgsConstructor
public class GetApplicationConfig {

  private final ApplicationRepository applicationRepository;
  private final ObjectMapper objectMapper;

  /**
   * Creates the gateway mapper bean.
   *
   * @return gateway mapper
   */
  @Bean
  public GetApplicationGatewayMapper getApplicationGatewayMapper() {
    return new GetApplicationGatewayMapper(objectMapper);
  }

  /**
   * Creates the gateway bean.
   *
   * @param getApplicationGatewayMapper gateway mapper
   * @return gateway
   */
  @Bean
  public GetApplicationApplicationJpaGateway getApplicationApplicationGateway(
      GetApplicationGatewayMapper getApplicationGatewayMapper) {
    return new GetApplicationApplicationJpaGateway(
        applicationRepository, getApplicationGatewayMapper);
  }

  /**
   * Creates the read model mapper bean.
   *
   * @return read model mapper
   */
  @Bean
  public GetApplicationReadModelMapper getApplicationReadModelMapper() {
    return new GetApplicationReadModelMapper();
  }

  /**
   * Creates the use case bean.
   *
   * @param getApplicationApplicationJpaGateway get-application gateway
   * @param getApplicationReadModelMapper read model mapper
   * @return use case
   */
  @Bean
  public GetApplicationUseCase getApplicationUseCase(
      GetApplicationApplicationJpaGateway getApplicationApplicationJpaGateway,
      GetApplicationReadModelMapper getApplicationReadModelMapper) {
    return new GetApplicationUseCase(
        getApplicationApplicationJpaGateway, getApplicationReadModelMapper);
  }

  /**
   * Creates the response mapper bean.
   *
   * @return response mapper
   */
  @Bean
  public GetApplicationResponseMapper getApplicationResponseMapper() {
    return new GetApplicationResponseMapper();
  }
}
