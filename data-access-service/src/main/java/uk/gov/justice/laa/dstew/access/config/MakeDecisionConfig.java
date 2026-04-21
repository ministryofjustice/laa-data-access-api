package uk.gov.justice.laa.dstew.access.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.CertificateJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.DecisionGatewayMapper;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.DecisionJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision.MakeDecisionDomainEventJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.DomainEventJpaGateway;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionCommandMapper;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionUseCase;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.CertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.DecisionGateway;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionDomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ProceedingGateway;

/** Wires all beans for the makeDecision use case. */
@Configuration
public class MakeDecisionConfig {

  /** Bean for the MakeDecisionUseCase. */
  @Bean
  public MakeDecisionUseCase makeDecisionUseCase(
      ApplicationGateway applicationGateway,
      ProceedingGateway proceedingGateway,
      DecisionGateway decisionGateway,
      CertificateGateway certificateGateway,
      MakeDecisionDomainEventGateway domainEventGateway) {
    return new MakeDecisionUseCase(
        applicationGateway,
        proceedingGateway,
        decisionGateway,
        certificateGateway,
        domainEventGateway);
  }

  /** Bean for the DecisionGateway. */
  @Bean
  public DecisionGateway decisionGateway(
      ApplicationRepository applicationRepo,
      DecisionRepository decisionRepo,
      MeritsDecisionRepository meritsDecisionRepo,
      ProceedingRepository proceedingRepo,
      DecisionGatewayMapper mapper) {
    return new DecisionJpaGateway(
        applicationRepo, decisionRepo, meritsDecisionRepo, proceedingRepo, mapper);
  }

  /** Bean for the CertificateGateway. */
  @Bean
  public CertificateGateway certificateGateway(CertificateRepository repo) {
    return new CertificateJpaGateway(repo);
  }

  /** Bean for the MakeDecisionDomainEventGateway. */
  @Bean
  public MakeDecisionDomainEventGateway makeDecisionDomainEventGateway(
      DomainEventJpaGateway sharedDomainEventGateway) {
    return new MakeDecisionDomainEventJpaGateway(sharedDomainEventGateway);
  }

  /** Bean for the DecisionGatewayMapper. */
  @Bean
  public DecisionGatewayMapper decisionGatewayMapper() {
    return new DecisionGatewayMapper();
  }

  /** Bean for the MakeDecisionCommandMapper. */
  @Bean
  public MakeDecisionCommandMapper makeDecisionCommandMapper(ObjectMapper objectMapper) {
    return new MakeDecisionCommandMapper(objectMapper);
  }
}
