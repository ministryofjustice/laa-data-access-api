package uk.gov.justice.laa.dstew.access.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.ApplicationGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.ApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.DomainEventJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.ProceedingGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.ProceedingJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ProceedingGateway;

/** Wires all beans for the createApplication use case. */
@Configuration
public class CreateApplicationConfig {

  @Bean
  public CreateApplicationUseCase createApplicationUseCase(
      ApplicationGateway applicationGateway,
      ProceedingGateway proceedingGateway,
      DomainEventGateway domainEventGateway,
      ApplicationContentParserService contentParser,
      ObjectMapper objectMapper) {
    return new CreateApplicationUseCase(
        applicationGateway, proceedingGateway, domainEventGateway, contentParser, objectMapper);
  }

  @Bean
  public ApplicationGateway applicationGateway(
      ApplicationRepository repo, ApplicationGatewayMapper mapper) {
    return new ApplicationJpaGateway(repo, mapper);
  }

  @Bean
  public ProceedingGateway proceedingGateway(
      ProceedingRepository repo, ProceedingGatewayMapper mapper) {
    return new ProceedingJpaGateway(repo, mapper);
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
  public ProceedingGatewayMapper proceedingGatewayMapper() {
    return new ProceedingGatewayMapper();
  }

  @Bean
  public CreateApplicationCommandMapper createApplicationCommandMapper(ObjectMapper objectMapper) {
    return new CreateApplicationCommandMapper(objectMapper);
  }
}
