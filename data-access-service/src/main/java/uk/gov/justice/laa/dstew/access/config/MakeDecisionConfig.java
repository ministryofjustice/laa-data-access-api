package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.controller.application.MakeDecisionCommandMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.MakeDecisionApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.MakeDecisionCertificateJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.MakeDecisionGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.MakeDecisionProceedingJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionUseCase;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** Spring configuration that wires all beans for the makeDecision use case. */
@Configuration
@RequiredArgsConstructor
public class MakeDecisionConfig {

  private final ApplicationRepository applicationRepository;
  private final CertificateRepository certificateRepository;
  private final ProceedingRepository proceedingRepository;
  private final SaveDomainEventService saveDomainEventService;
  private final ObjectMapper objectMapper;

  /** Injected shared gateway — used for read operations (findByApplicationId). */
  private final ApplicationGateway applicationGateway;

  /** Creates the certificate gateway mapper bean. */
  @Bean
  public MakeDecisionGatewayMapper makeDecisionGatewayMapper() {
    return new MakeDecisionGatewayMapper();
  }

  /** Creates the certificate JPA gateway bean. */
  @Bean
  public MakeDecisionCertificateJpaGateway makeDecisionCertificateGateway(
      MakeDecisionGatewayMapper makeDecisionGatewayMapper) {
    return new MakeDecisionCertificateJpaGateway(certificateRepository, makeDecisionGatewayMapper);
  }

  /** Creates the proceeding JPA gateway bean. */
  @Bean
  public MakeDecisionProceedingJpaGateway makeDecisionProceedingGateway() {
    return new MakeDecisionProceedingJpaGateway(proceedingRepository);
  }

  /** Creates the command mapper bean. */
  @Bean
  public MakeDecisionCommandMapper makeDecisionCommandMapper() {
    return new MakeDecisionCommandMapper(objectMapper);
  }

  /** Creates the application JPA gateway bean for decision-update operations. */
  @Bean
  public MakeDecisionApplicationJpaGateway makeDecisionApplicationGateway(
      MakeDecisionGatewayMapper makeDecisionGatewayMapper) {
    return new MakeDecisionApplicationJpaGateway(applicationRepository, makeDecisionGatewayMapper);
  }

  /**
   * Creates the {@link MakeDecisionUseCase} bean.
   *
   * @param makeDecisionApplicationGateway the gateway for decision-update operations
   * @param makeDecisionCertificateGateway the certificate gateway
   * @param makeDecisionProceedingGateway the proceeding gateway
   * @return a fully configured use case
   */
  @Bean
  public MakeDecisionUseCase makeDecisionUseCase(
      MakeDecisionApplicationJpaGateway makeDecisionApplicationGateway,
      MakeDecisionCertificateJpaGateway makeDecisionCertificateGateway,
      MakeDecisionProceedingJpaGateway makeDecisionProceedingGateway) {
    return new MakeDecisionUseCase(
        applicationGateway,
        makeDecisionApplicationGateway,
        makeDecisionCertificateGateway,
        makeDecisionProceedingGateway,
        saveDomainEventService);
  }
}
