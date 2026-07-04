package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.controller.application.MakeDecisionCommandMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.MakeDecisionCertificateJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.MakeDecisionGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.MakeDecisionProceedingJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application.ApplicationGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application.ApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionUseCase;

/** Spring configuration that wires all beans for the makeDecision use case. */
@Configuration
@RequiredArgsConstructor
public class MakeDecisionConfig {

  private final ApplicationRepository applicationRepository;
  private final CertificateRepository certificateRepository;
  private final ProceedingRepository proceedingRepository;
  private final SaveDomainEventService saveDomainEventService;
  private final ObjectMapper objectMapper;

  /** Injected from {@link CreateApplicationConfig} — the single ApplicationGatewayMapper bean. */
  private final ApplicationGatewayMapper applicationGatewayMapper;

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

  /**
   * Creates the {@link MakeDecisionUseCase} bean. The {@link ApplicationJpaGateway} for this use
   * case is constructed locally to avoid exposing a second {@code ApplicationGateway} bean in the
   * context (which would cause ambiguity for other configs that inject by type).
   *
   * @param makeDecisionCertificateGateway the certificate gateway
   * @param makeDecisionProceedingGateway the proceeding gateway
   * @return a fully configured use case
   */
  @Bean
  public MakeDecisionUseCase makeDecisionUseCase(
      MakeDecisionCertificateJpaGateway makeDecisionCertificateGateway,
      MakeDecisionProceedingJpaGateway makeDecisionProceedingGateway) {
    ApplicationJpaGateway applicationGateway =
        new ApplicationJpaGateway(applicationRepository, applicationGatewayMapper);
    return new MakeDecisionUseCase(
        applicationGateway,
        makeDecisionCertificateGateway,
        makeDecisionProceedingGateway,
        saveDomainEventService);
  }
}
