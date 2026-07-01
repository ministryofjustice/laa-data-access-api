package uk.gov.justice.laa.dstew.access.infrastructure.jpa.assigncaseworker;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.model.AssignCaseworkerApplication;

/** Maps {@link ApplicationEntity} to {@link AssignCaseworkerApplication}. */
public class AssignCaseworkerGatewayMapper {

  /**
   * Maps an {@link ApplicationEntity} to an {@link AssignCaseworkerApplication} carrying only the
   * fields required by the assignCaseworker use case: {@code id} and {@code caseworkerId}.
   */
  public AssignCaseworkerApplication toReadModel(ApplicationEntity application) {
    return AssignCaseworkerApplication.builder()
        .id(application.getId())
        .caseworkerId(
            application.getCaseworker() != null ? application.getCaseworker().getId() : null)
        .build();
  }
}
