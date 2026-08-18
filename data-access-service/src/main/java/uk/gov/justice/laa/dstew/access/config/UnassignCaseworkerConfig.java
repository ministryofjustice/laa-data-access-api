package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.controller.application.UnassignCaseworkerCommandMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.unassigncaseworker.UnassignCaseworkerApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.unassigncaseworker.UnassignCaseworkerGatewayMapper;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.UnassignCaseworkerUseCase;

/** Spring configuration that wires all beans for the unassignCaseworker use case. */
@Configuration
@RequiredArgsConstructor
public class UnassignCaseworkerConfig {

  private final ApplicationRepository applicationRepository;
  private final SaveDomainEventService saveDomainEventService;

  /** Creates the {@link UnassignCaseworkerGatewayMapper} bean. */
  @Bean
  public UnassignCaseworkerGatewayMapper unassignCaseworkerGatewayMapper() {
    return new UnassignCaseworkerGatewayMapper();
  }

  /** Creates the {@link UnassignCaseworkerApplicationJpaGateway} bean. */
  @Bean
  public UnassignCaseworkerApplicationJpaGateway unassignCaseworkerApplicationJpaGateway(
      UnassignCaseworkerGatewayMapper gatewayMapper) {
    return new UnassignCaseworkerApplicationJpaGateway(applicationRepository, gatewayMapper);
  }

  /** Creates the {@link UnassignCaseworkerCommandMapper} bean. */
  @Bean
  public UnassignCaseworkerCommandMapper unassignCaseworkerCommandMapper() {
    return new UnassignCaseworkerCommandMapper();
  }

  /**
   * Creates the {@link UnassignCaseworkerUseCase} bean.
   *
   * @param applicationGateway the application gateway
   * @param unassignCaseworkerApplicationJpaGateway the unassign application gateway
   * @return a fully configured use case
   */
  @Bean
  public UnassignCaseworkerUseCase unassignCaseworkerUseCase(
      ApplicationGateway applicationGateway,
      UnassignCaseworkerApplicationJpaGateway unassignCaseworkerApplicationJpaGateway) {
    return new UnassignCaseworkerUseCase(
        applicationGateway, unassignCaseworkerApplicationJpaGateway, saveDomainEventService);
  }
}
