package uk.gov.justice.laa.dstew.access.config;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application.ApplicationGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application.ApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application.LinkedApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.LinkedApplicationRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationDomainMapper;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.PayloadValidator;

/** Spring configuration that wires all beans for the createApplication use case. */
@Configuration
@RequiredArgsConstructor
public class CreateApplicationConfig {

  private final ApplicationRepository applicationRepository;
  private final LinkedApplicationRepository linkedApplicationRepository;
  private final SaveDomainEventService saveDomainEventService;
  private final ObjectMapper objectMapper;
  private final Validator validator;

  @Bean
  public ApplicationGatewayMapper createApplicationGatewayMapper() {
    return new ApplicationGatewayMapper(objectMapper);
  }

  @Bean
  public ApplicationJpaGateway createApplicationApplicationGateway(
      ApplicationGatewayMapper gatewayMapper) {
    return new ApplicationJpaGateway(applicationRepository, gatewayMapper);
  }

  @Bean
  public LinkedApplicationJpaGateway createApplicationLinkedApplicationGateway() {
    return new LinkedApplicationJpaGateway(linkedApplicationRepository);
  }

  @Bean
  public PayloadValidator payloadValidationService() {
    return new PayloadValidator(objectMapper, validator);
  }

  @Bean
  public ApplicationContentParser applicationContentParser(
      PayloadValidator payloadValidationService) {
    return new ApplicationContentParser(payloadValidationService);
  }

  @Bean
  public CreateApplicationDomainMapper createApplicationDomainMapper() {
    return new CreateApplicationDomainMapper();
  }

  /**
   * Creates the {@link CreateApplicationUseCase} bean.
   *
   * @param applicationGateway the application gateway
   * @param linkedApplicationGateway the linked-application gateway
   * @param applicationContentParser the content parser
   * @param domainMapper the domain mapper
   * @return a fully configured use case
   */
  @Bean
  public CreateApplicationUseCase createApplicationUseCase(
      ApplicationJpaGateway applicationGateway,
      LinkedApplicationJpaGateway linkedApplicationGateway,
      ApplicationContentParser applicationContentParser,
      CreateApplicationDomainMapper domainMapper) {
    return new CreateApplicationUseCase(
        applicationGateway,
        linkedApplicationGateway,
        applicationContentParser,
        domainMapper,
        saveDomainEventService);
  }

  @Bean
  public CreateApplicationCommandMapper createApplicationCommandMapper() {
    return new CreateApplicationCommandMapper(objectMapper);
  }
}
