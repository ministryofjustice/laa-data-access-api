package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate.GetCertificateCertificateJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate.GetCertificateGatewayMapper;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.GetCertificateUseCase;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** Spring configuration that wires all beans for the getCertificate use case. */
@Configuration
@RequiredArgsConstructor
public class GetCertificateConfig {

  private final CertificateRepository certificateRepository;

  /** Creates the gateway mapper bean. */
  @Bean
  public GetCertificateGatewayMapper getCertificateGatewayMapper() {
    return new GetCertificateGatewayMapper();
  }

  /**
   * Creates the certificate JPA gateway bean.
   *
   * @param getCertificateGatewayMapper gateway mapper
   * @return certificate gateway
   */
  @Bean
  public GetCertificateCertificateJpaGateway getCertificateCertificateGateway(
      GetCertificateGatewayMapper getCertificateGatewayMapper) {
    return new GetCertificateCertificateJpaGateway(
        certificateRepository, getCertificateGatewayMapper);
  }

  /**
   * Creates the use case bean.
   *
   * @param applicationGateway application gateway
   * @param getCertificateCertificateGateway certificate gateway
   * @return use case
   */
  @Bean
  public GetCertificateUseCase getCertificateUseCase(
      ApplicationGateway applicationGateway,
      GetCertificateCertificateJpaGateway getCertificateCertificateGateway) {
    return new GetCertificateUseCase(applicationGateway, getCertificateCertificateGateway);
  }
}
