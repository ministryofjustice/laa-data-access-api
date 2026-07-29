package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.UnassignCaseworkerCommand;

/**
 * Maps {@link CaseworkerUnassignRequest} and a path variable to {@link UnassignCaseworkerCommand}.
 */
public class UnassignCaseworkerCommandMapper {

  /**
   * Converts the path-variable {@code id} and {@link CaseworkerUnassignRequest} to an {@link
   * UnassignCaseworkerCommand}.
   *
   * @param id the application UUID from the path variable
   * @param caseworkerUnassignRequest the HTTP request model
   * @return the command record
   */
  public UnassignCaseworkerCommand toUnassignCaseworkerCommand(
      UUID id, CaseworkerUnassignRequest caseworkerUnassignRequest) {
    return UnassignCaseworkerCommand.builder()
        .applicationId(id)
        .eventDescription(
            caseworkerUnassignRequest.getEventHistory() != null
                ? caseworkerUnassignRequest.getEventHistory().getEventDescription()
                : null)
        .build();
  }
}
