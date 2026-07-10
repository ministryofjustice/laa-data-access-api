package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application.ApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateApplicationGateway;

/** JPA implementation of {@link GetCertificateApplicationGateway}. */
public class GetCertificateApplicationJpaGateway implements GetCertificateApplicationGateway {

  private final ApplicationJpaGateway applicationJpaGateway;

  /**
   * Constructs the gateway.
   *
   * @param applicationJpaGateway the Spring Data repository
   */
  public GetCertificateApplicationJpaGateway(ApplicationJpaGateway applicationJpaGateway) {
    this.applicationJpaGateway = applicationJpaGateway;
  }

  @Override
  public boolean applicationExists(UUID applicationId) {
    return applicationJpaGateway.applicationExists(applicationId);
  }
}
