package uk.gov.justice.laa.dstew.access.controller.application;

import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.AssignCaseworkerCommand;

/** Maps {@link CaseworkerAssignRequest} to {@link AssignCaseworkerCommand}. */
public class AssignCaseworkerCommandMapper {

  /**
   * Converts a {@link CaseworkerAssignRequest} to an {@link AssignCaseworkerCommand}.
   *
   * @param caseworkerAssignRequest the HTTP request model
   * @return the command record
   */
  public AssignCaseworkerCommand toAssignCaseworkerCommand(
      CaseworkerAssignRequest caseworkerAssignRequest) {
    return AssignCaseworkerCommand.builder()
        .caseworkerId(caseworkerAssignRequest.getCaseworkerId())
        .applicationIds(caseworkerAssignRequest.getApplicationIds())
        .eventDescription(
            caseworkerAssignRequest.getEventHistory() != null
                ? caseworkerAssignRequest.getEventHistory().getEventDescription()
                : null)
        .build();
  }
}
