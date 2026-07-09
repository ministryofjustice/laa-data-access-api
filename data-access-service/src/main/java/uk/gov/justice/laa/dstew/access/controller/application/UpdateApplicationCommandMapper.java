package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.usecase.updateapplication.UpdateApplicationCommand;

/** Maps {@link ApplicationUpdateRequest} to {@link UpdateApplicationCommand}. */
public class UpdateApplicationCommandMapper {

  /**
   * Converts an {@link ApplicationUpdateRequest} and application ID to an {@link
   * UpdateApplicationCommand}.
   *
   * @param id the application UUID from the path parameter
   * @param applicationUpdateRequest the HTTP request model
   * @return the command record
   */
  public UpdateApplicationCommand toUpdateApplicationCommand(
      UUID id, ApplicationUpdateRequest applicationUpdateRequest) {
    return UpdateApplicationCommand.builder()
        .id(id)
        .status(
            applicationUpdateRequest.getStatus() != null
                ? applicationUpdateRequest.getStatus().name()
                : null)
        .applicationContent(applicationUpdateRequest.getApplicationContent())
        .build();
  }
}
