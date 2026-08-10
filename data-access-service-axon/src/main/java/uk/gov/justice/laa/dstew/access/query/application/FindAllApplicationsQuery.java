package uk.gov.justice.laa.dstew.access.query.application;

import java.time.LocalDate;
import uk.gov.justice.laa.dstew.access.query.PaginationHelper;

/**
 * Query to retrieve a paginated, filtered list of Applications.
 *
 * <p>All filter fields including {@code clientFirstName}, {@code clientLastName}, and {@code
 * clientDateOfBirth} are applied as database predicates against {@code application_list_index}.
 * After paging, rich response fields are bulk-loaded from {@code application_data} only for the
 * returned page.
 */
public record FindAllApplicationsQuery(
    String status,
    String laaReference,
    String matterType,
    String clientFirstName,
    String clientLastName,
    LocalDate clientDateOfBirth,
    Boolean isAutoGranted,
    String sortBy,
    String orderBy,
    Integer page,
    Integer pageSize) {

  /** Backwards-compatible constructor for callers that do not filter by auto-grant outcome. */
  public FindAllApplicationsQuery(
      String status,
      String laaReference,
      String matterType,
      String clientFirstName,
      String clientLastName,
      LocalDate clientDateOfBirth,
      String sortBy,
      String orderBy,
      Integer page,
      Integer pageSize) {
    this(
        status,
        laaReference,
        matterType,
        clientFirstName,
        clientLastName,
        clientDateOfBirth,
        null,
        sortBy,
        orderBy,
        page,
        pageSize);
  }

  /** Resolves defaults and validates the shared pagination constraints. */
  public FindAllApplicationsQuery {
    page = PaginationHelper.validatePage(page);
    pageSize = PaginationHelper.validatePageSize(pageSize);
  }
}
