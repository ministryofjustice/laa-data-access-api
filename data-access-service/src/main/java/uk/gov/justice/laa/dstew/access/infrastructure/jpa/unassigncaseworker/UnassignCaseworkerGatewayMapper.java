package uk.gov.justice.laa.dstew.access.infrastructure.jpa.unassigncaseworker;

import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;

/**
 * Maps {@link ApplicationEntity} to {@link ApplicationDomain} for the unassignCaseworker use case.
 */
public class UnassignCaseworkerGatewayMapper {

  /**
   * Converts an {@link ApplicationEntity} to an {@link ApplicationDomain} carrying the fields
   * required by the unassignCaseworker use case: {@code id} and {@code caseworkerId}.
   *
   * @param application the JPA entity to convert
   * @return the domain record
   */
  public ApplicationDomain toApplicationDomain(ApplicationEntity application) {
    return ApplicationDomain.builder()
        .id(application.getId())
        .caseworkerId(
            application.getCaseworker() != null ? application.getCaseworker().getId() : null)
        .build();
  }
}
