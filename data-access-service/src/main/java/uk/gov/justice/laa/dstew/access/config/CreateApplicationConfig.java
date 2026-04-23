package uk.gov.justice.laa.dstew.access.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.ApplicationGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.ApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.DomainEventJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepositoryV2;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;

/** Wires all beans for the createApplication use case. */
@Configuration
public class CreateApplicationConfig {

  @Bean
  public CreateApplicationUseCase createApplicationUseCase(
      ApplicationGateway applicationGateway,
      DomainEventGateway domainEventGateway,
      ApplicationContentParserService contentParser,
      ObjectMapper objectMapper) {
    return new CreateApplicationUseCase(
        applicationGateway, domainEventGateway, contentParser, objectMapper);
  }

  @Bean
  public ApplicationGateway applicationGateway(
      ApplicationRepositoryV2 repo, ApplicationGatewayMapper mapper) {
    return new ApplicationJpaGateway(repo, mapper);
  }

  @Bean
  public DomainEventJpaGateway domainEventGateway(DomainEventService domainEventService) {
    return new DomainEventJpaGateway(domainEventService);
  }

  @Bean
  public ApplicationGatewayMapper applicationGatewayMapper() {
    return new ApplicationGatewayMapper();
  }

  @Bean
  public CreateApplicationCommandMapper createApplicationCommandMapper(ObjectMapper objectMapper) {
    return new CreateApplicationCommandMapper(objectMapper);
  }
}
