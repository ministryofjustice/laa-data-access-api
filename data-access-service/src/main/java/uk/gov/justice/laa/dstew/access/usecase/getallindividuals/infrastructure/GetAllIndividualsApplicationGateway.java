package uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;

/** Gateway interface for fetching client details from an application. */
public interface GetAllIndividualsApplicationGateway {

  /**
   * Returns the client details for the given application.
   *
   * @param applicationId the application UUID
   * @return client details domain record
   * @throws java.util.NoSuchElementException if no application exists for the given id
   */
  ApplicationClientDetailsDomain findClientDetails(UUID applicationId);
}
