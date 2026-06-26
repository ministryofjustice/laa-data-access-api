package uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker;

import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;

/**
 * Maps {@link ApplicationEntity} to {@link ApplicationDomain} for the assignCaseworker use case.
 */
public class AssignCaseworkerGatewayMapper {

  /**
   * Converts an {@link ApplicationEntity} to an {@link ApplicationDomain} carrying only the fields
   * relevant to this use case: {@code id} and {@code caseworkerId}.
   *
   * @param application the JPA entity
   * @return the domain projection
   */
  public ApplicationDomain toApplicationDomain(ApplicationEntity application) {
    return ApplicationDomain.builder()
        .id(application.getId())
        .caseworkerId(
            application.getCaseworker() != null ? application.getCaseworker().getId() : null)
        .build();
  }
}
