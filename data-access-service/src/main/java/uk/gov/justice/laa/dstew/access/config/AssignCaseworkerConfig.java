package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.controller.application.AssignCaseworkerCommandMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker.AssignCaseworkerApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker.AssignCaseworkerCaseworkerJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker.AssignCaseworkerGatewayMapper;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.AssignCaseworkerUseCase;

/** Spring configuration that wires all beans for the assignCaseworker use case. */
@Configuration
@RequiredArgsConstructor
public class AssignCaseworkerConfig {

  private final ApplicationRepository applicationRepository;
  private final CaseworkerRepository caseworkerRepository;
  private final SaveDomainEventService saveDomainEventService;

  /** Creates the {@link AssignCaseworkerGatewayMapper} bean. */
  @Bean
  public AssignCaseworkerGatewayMapper assignCaseworkerGatewayMapper() {
    return new AssignCaseworkerGatewayMapper();
  }

  /** Creates the {@link AssignCaseworkerApplicationJpaGateway} bean. */
  @Bean
  public AssignCaseworkerApplicationJpaGateway assignCaseworkerApplicationJpaGateway(
      AssignCaseworkerGatewayMapper gatewayMapper) {
    return new AssignCaseworkerApplicationJpaGateway(
        applicationRepository, caseworkerRepository, gatewayMapper);
  }

  /** Creates the {@link AssignCaseworkerCaseworkerJpaGateway} bean. */
  @Bean
  public AssignCaseworkerCaseworkerJpaGateway assignCaseworkerCaseworkerJpaGateway() {
    return new AssignCaseworkerCaseworkerJpaGateway(caseworkerRepository);
  }

  /** Creates the {@link AssignCaseworkerCommandMapper} bean. */
  @Bean
  public AssignCaseworkerCommandMapper assignCaseworkerCommandMapper() {
    return new AssignCaseworkerCommandMapper();
  }

  /**
   * Creates the {@link AssignCaseworkerUseCase} bean.
   *
   * @param applicationJpaGateway the application gateway
   * @param caseworkerJpaGateway the caseworker gateway
   * @return a fully configured use case
   */
  @Bean
  public AssignCaseworkerUseCase assignCaseworkerUseCase(
      AssignCaseworkerApplicationJpaGateway applicationJpaGateway,
      AssignCaseworkerCaseworkerJpaGateway caseworkerJpaGateway) {
    return new AssignCaseworkerUseCase(
        applicationJpaGateway, caseworkerJpaGateway, saveDomainEventService);
  }
}
