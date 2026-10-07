package uk.gov.justice.laa.dstew.access.query.application;

import java.time.LocalDate;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.query.PaginationHelper;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;

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
    String matterTypeCode,
    String clientFirstName,
    String clientLastName,
    LocalDate clientDateOfBirth,
    AutoGrantedState autoGranted,
    String sortBy,
    String orderBy,
    Integer page,
    Integer pageSize,
    ReadAccessScope accessScope) {

  /** Backwards-compatible constructor for callers that do not filter by auto-grant outcome. */
  public FindAllApplicationsQuery(
      String status,
      String laaReference,
      String matterTypeCode,
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
        matterTypeCode,
        clientFirstName,
        clientLastName,
        clientDateOfBirth,
        null,
        sortBy,
        orderBy,
        page,
        pageSize,
        new UnrestrictedReadAccessScope());
  }

  /** Backwards-compatible constructor for callers that include auto-grant filtering. */
  public FindAllApplicationsQuery(
      String status,
      String laaReference,
      String matterTypeCode,
      String clientFirstName,
      String clientLastName,
      LocalDate clientDateOfBirth,
      AutoGrantedState autoGranted,
      String sortBy,
      String orderBy,
      Integer page,
      Integer pageSize) {
    this(
        status,
        laaReference,
        matterTypeCode,
        clientFirstName,
        clientLastName,
        clientDateOfBirth,
        autoGranted,
        sortBy,
        orderBy,
        page,
        pageSize,
        new UnrestrictedReadAccessScope());
  }

  /** Resolves defaults and validates the shared pagination constraints. */
  public FindAllApplicationsQuery {
    page = PaginationHelper.validatePage(page);
    pageSize = PaginationHelper.validatePageSize(pageSize);
    accessScope = accessScope == null ? new UnrestrictedReadAccessScope() : accessScope;
  }
}
