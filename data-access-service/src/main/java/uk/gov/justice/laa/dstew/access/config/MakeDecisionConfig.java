package uk.gov.justice.laa.dstew.access.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.CertificateJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionCommandMapper;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionUseCase;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.CertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;

/** Wires all beans for the makeDecision use case. */
@Configuration
public class MakeDecisionConfig {

  /** Bean for the MakeDecisionUseCase. */
  @Bean
  public MakeDecisionUseCase makeDecisionUseCase(
      ApplicationGateway applicationGateway,
      CertificateGateway certificateGateway,
      DomainEventGateway domainEventGateway) {
    return new MakeDecisionUseCase(applicationGateway, certificateGateway, domainEventGateway);
  }

  /** Bean for the CertificateGateway. */
  @Bean
  public CertificateGateway certificateGateway(CertificateRepository repo) {
    return new CertificateJpaGateway(repo);
  }

  /** Bean for the MakeDecisionCommandMapper. */
  @Bean
  public MakeDecisionCommandMapper makeDecisionCommandMapper(ObjectMapper objectMapper) {
    return new MakeDecisionCommandMapper(objectMapper);
  }
}
