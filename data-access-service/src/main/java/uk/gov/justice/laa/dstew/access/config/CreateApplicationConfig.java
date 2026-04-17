package uk.gov.justice.laa.dstew.access.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication.ApplicationGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication.ApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication.DomainEventJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication.ProceedingGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication.ProceedingJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.ProceedingGateway;

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
  public DomainEventGateway domainEventGateway(DomainEventService domainEventService) {
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
  public CreateApplicationCommandMapper createApplicationCommandMapper() {
    return new CreateApplicationCommandMapper();
  }
}
