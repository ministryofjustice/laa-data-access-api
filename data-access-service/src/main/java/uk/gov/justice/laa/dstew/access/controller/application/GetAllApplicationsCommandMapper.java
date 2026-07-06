package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.LocalDate;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsCommand;

/**
 * Maps controller query parameters to a {@link GetAllApplicationsCommand}. This is the only class
 * permitted to import API model enum types for the getAllApplications use case; it converts them to
 * plain Strings via {@code .name()} with null guards.
 */
public class GetAllApplicationsCommandMapper {

  /**
   * Converts the controller query parameters to a {@link GetAllApplicationsCommand}. {@code
   * ServiceName} is consumed at the controller level and excluded from the command.
   *
   * @param status optional application status filter
   * @param laaReference optional LAA reference filter
   * @param clientFirstName optional client first-name filter
   * @param clientLastName optional client last-name filter
   * @param clientDateOfBirth optional client date-of-birth filter
   * @param userId optional caseworker ID filter
   * @param isAutoGranted optional auto-grant filter
   * @param matterType optional matter-type filter
   * @param sortBy optional sort field
   * @param orderBy optional sort direction
   * @param page one-based page number
   * @param pageSize number of results per page
   * @return the command record
   */
  public GetAllApplicationsCommand toGetAllApplicationsCommand(
      ApplicationStatus status,
      String laaReference,
      String clientFirstName,
      String clientLastName,
      LocalDate clientDateOfBirth,
      UUID userId,
      Boolean isAutoGranted,
      MatterType matterType,
      ApplicationSortBy sortBy,
      ApplicationOrderBy orderBy,
      Integer page,
      Integer pageSize) {
    return GetAllApplicationsCommand.builder()
        .status(status == null ? null : status.name())
        .laaReference(laaReference)
        .clientFirstName(clientFirstName)
        .clientLastName(clientLastName)
        .clientDateOfBirth(clientDateOfBirth)
        .userId(userId)
        .isAutoGranted(isAutoGranted)
        .matterType(matterType == null ? null : matterType.name())
        .sortBy(sortBy == null ? null : sortBy.name())
        .orderBy(orderBy == null ? null : orderBy.name())
        .page(page)
        .pageSize(pageSize)
        .build();
  }
}
