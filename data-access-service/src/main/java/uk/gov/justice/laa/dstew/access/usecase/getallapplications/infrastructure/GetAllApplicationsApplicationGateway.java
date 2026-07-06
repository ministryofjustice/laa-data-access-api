package uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;

/** Gateway interface for paginated application queries in the getAllApplications use case. */
public interface GetAllApplicationsApplicationGateway {

  /**
   * Retrieves a page of application summaries matching the provided filters.
   *
   * @param status optional status filter (enum name string)
   * @param laaReference optional reference filter
   * @param clientFirstName optional first-name filter
   * @param clientLastName optional last-name filter
   * @param clientDateOfBirth optional date-of-birth filter
   * @param userId optional caseworker ID filter
   * @param matterType optional matter-type filter (enum name string)
   * @param isAutoGranted optional auto-grant filter
   * @param sortBy optional sort field (enum name string; SUBMITTED_DATE default)
   * @param orderBy optional sort direction (enum name string; ASC default)
   * @param page one-based page number
   * @param pageSize number of results per page
   * @return page of domain summaries
   */
  Page<ApplicationSummaryDomain> findAllApplications(
      String status,
      String laaReference,
      String clientFirstName,
      String clientLastName,
      LocalDate clientDateOfBirth,
      UUID userId,
      String matterType,
      Boolean isAutoGranted,
      String sortBy,
      String orderBy,
      Integer page,
      Integer pageSize);

  /**
   * Retrieves all linked-application summaries for the given page of application IDs.
   *
   * @param pageIds the IDs of applications in the current page
   * @return list of linked-application domain records
   */
  List<LinkedApplicationSummaryDomain> findLinkedApplicationsForPageIds(List<UUID> pageIds);
}
