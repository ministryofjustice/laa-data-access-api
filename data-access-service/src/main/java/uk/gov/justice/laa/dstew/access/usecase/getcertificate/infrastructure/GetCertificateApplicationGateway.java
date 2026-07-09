package uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure;

import java.util.UUID;

/** Gateway for checking application existence in the getCertificate use case. */
public interface GetCertificateApplicationGateway {

  /**
   * Returns true if an application with the given id exists.
   *
   * @param applicationId application id
   * @return true if found
   */
  boolean applicationExists(UUID applicationId);
}
