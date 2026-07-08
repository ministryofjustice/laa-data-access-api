package uk.gov.justice.laa.dstew.access.usecase.getallapplications.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.PagedResultDomain;

/** Gateway interface for paginated application queries in the getAllApplications use case. */
public interface GetAllApplicationsApplicationGateway {

  /**
   * Finds a page of application summaries matching the provided filters.
   *
   * @param status optional status filter (enum name)
   * @param matterType optional matter-type filter (enum name)
   * @param sortBy optional sort field (enum name; defaults to SUBMITTED_DATE)
   * @param orderBy optional sort direction (enum name; defaults to ASC)
   * @param page one-based page number
   * @param pageSize number of results per page
   * @return page of domain summaries
   */
  PagedResultDomain<ApplicationSummaryDomain> findAllApplications(
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
