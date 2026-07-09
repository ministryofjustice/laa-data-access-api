package uk.gov.justice.laa.dstew.access.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate.GetCertificateApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate.GetCertificateCertificateJpaGateway;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate.GetCertificateGatewayMapper;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.GetCertificateUseCase;

/** Spring configuration for the get-certificate use case. */
@Configuration
@RequiredArgsConstructor
public class GetCertificateConfig {

  private final ApplicationRepository applicationRepository;
  private final CertificateRepository certificateRepository;

  /**
   * Creates the gateway mapper bean.
   *
   * @return gateway mapper
   */
  @Bean
  public GetCertificateGatewayMapper getCertificateGatewayMapper() {
    return new GetCertificateGatewayMapper();
  }

  /**
   * Creates the application gateway bean.
   *
   * @return application gateway
   */
  @Bean
  public GetCertificateApplicationJpaGateway getCertificateApplicationJpaGateway() {
    return new GetCertificateApplicationJpaGateway(applicationRepository);
  }

  /**
   * Creates the certificate gateway bean.
   *
   * @param getCertificateGatewayMapper gateway mapper
   * @return certificate gateway
   */
  @Bean
  public GetCertificateCertificateJpaGateway getCertificateCertificateJpaGateway(
      GetCertificateGatewayMapper getCertificateGatewayMapper) {
    return new GetCertificateCertificateJpaGateway(
        certificateRepository, getCertificateGatewayMapper);
  }

  /**
   * Creates the use case bean.
   *
   * @param getCertificateApplicationJpaGateway application gateway
   * @param getCertificateCertificateJpaGateway certificate gateway
   * @return use case
   */
  @Bean
  public GetCertificateUseCase getCertificateUseCase(
      GetCertificateApplicationJpaGateway getCertificateApplicationJpaGateway,
      GetCertificateCertificateJpaGateway getCertificateCertificateJpaGateway) {
    return new GetCertificateUseCase(
        getCertificateApplicationJpaGateway, getCertificateCertificateJpaGateway);
  }
}
