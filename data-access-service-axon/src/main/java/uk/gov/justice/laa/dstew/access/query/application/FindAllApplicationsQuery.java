package uk.gov.justice.laa.dstew.access.query.application;

import java.time.LocalDate;
import uk.gov.justice.laa.dstew.access.query.PaginationHelper;

/**
 * Query to retrieve a paginated, filtered list of Application current-state projections.
 *
 * <p>{@code clientFirstName}, {@code clientLastName}, and {@code clientDateOfBirth} are accepted
 * for API compatibility but are not yet applied as filters — client data is stored in the {@code
 * individuals} JSON column rather than as plain columns. A future migration can denormalise those
 * fields to enable filtering.
 */
public record FindAllApplicationsQuery(
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

  /** Resolves defaults and validates the shared pagination constraints. */
  public FindAllApplicationsQuery {
    page = PaginationHelper.validatePage(page);
    pageSize = PaginationHelper.validatePageSize(pageSize);
  }
}
