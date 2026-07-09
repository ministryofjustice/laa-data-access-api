package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.controller.individual.GetAllIndividualsQueryMapper;
import uk.gov.justice.laa.dstew.access.controller.individual.GetAllIndividualsResponseMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallindividuals.GetAllIndividualsApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallindividuals.GetAllIndividualsGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallindividuals.GetAllIndividualsIndividualJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.GetAllIndividualsUseCase;

/** Spring configuration for the get-all-individuals use case. */
@Configuration
@RequiredArgsConstructor
public class GetAllIndividualsConfig {

  private final IndividualRepository individualRepository;
  private final ApplicationRepository applicationRepository;
  private final ObjectMapper objectMapper;

  /**
   * Creates the gateway mapper bean.
   *
   * @return gateway mapper
   */
  @Bean
  public GetAllIndividualsGatewayMapper getAllIndividualsGatewayMapper() {
    return new GetAllIndividualsGatewayMapper(objectMapper);
  }

  /**
   * Creates the individual JPA gateway bean.
   *
   * @param getAllIndividualsGatewayMapper gateway mapper
   * @return individual JPA gateway
   */
  @Bean
  public GetAllIndividualsIndividualJpaGateway getAllIndividualsIndividualJpaGateway(
      GetAllIndividualsGatewayMapper getAllIndividualsGatewayMapper) {
    return new GetAllIndividualsIndividualJpaGateway(
        individualRepository, getAllIndividualsGatewayMapper);
  }

  /**
   * Creates the application JPA gateway bean.
   *
   * @param getAllIndividualsGatewayMapper gateway mapper
   * @return application JPA gateway
   */
  @Bean
  public GetAllIndividualsApplicationJpaGateway getAllIndividualsApplicationJpaGateway(
      GetAllIndividualsGatewayMapper getAllIndividualsGatewayMapper) {
    return new GetAllIndividualsApplicationJpaGateway(
        applicationRepository, getAllIndividualsGatewayMapper);
  }

  /**
   * Creates the use case bean.
   *
   * @param getAllIndividualsIndividualJpaGateway individual gateway
   * @param getAllIndividualsApplicationJpaGateway application gateway
   * @return use case
   */
  @Bean
  public GetAllIndividualsUseCase getAllIndividualsUseCase(
      GetAllIndividualsIndividualJpaGateway getAllIndividualsIndividualJpaGateway,
      GetAllIndividualsApplicationJpaGateway getAllIndividualsApplicationJpaGateway) {
    return new GetAllIndividualsUseCase(
        getAllIndividualsIndividualJpaGateway, getAllIndividualsApplicationJpaGateway);
  }

  /**
   * Creates the response mapper bean.
   *
   * @return response mapper
   */
  @Bean
  public GetAllIndividualsResponseMapper getAllIndividualsResponseMapper() {
    return new GetAllIndividualsResponseMapper();
  }

  /**
   * Creates the query mapper bean.
   *
   * @return query mapper
   */
  @Bean
  public GetAllIndividualsQueryMapper getAllIndividualsQueryMapper() {
    return new GetAllIndividualsQueryMapper();
  }
}
