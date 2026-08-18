package uk.gov.justice.laa.dstew.access.query.individual;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.query.PaginationHelper;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Query for a filtered, paginated view of individuals in current application data. */
public record FindIndividualsQuery(
    UUID applicationId,
    String individualType,
    boolean includeClientDetails,
    Integer page,
    Integer pageSize) {

  /** Validates query relationships and resolves optional pagination values. */
  public FindIndividualsQuery {
    if (includeClientDetails && applicationId == null) {
      throw new ValidationException(
          java.util.List.of("Application ID is required when included data is CLIENT_DETAILS"));
    }
    page = PaginationHelper.validatePage(page);
    pageSize = PaginationHelper.validatePageSize(pageSize);
  }
}
