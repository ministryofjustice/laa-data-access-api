package uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.PagedResult;

/** Gateway interface for paginated individual queries. */
public interface GetAllIndividualsIndividualGateway {

  /**
   * Returns a page of individuals matching the given filters.
   *
   * @param applicationId optional application UUID filter
   * @param individualType optional individual type filter
   * @param page one-based page number
   * @param pageSize page size
   * @return page of matching individuals
   */
  PagedResult<IndividualDomain> findAll(
      UUID applicationId, String individualType, int page, int pageSize);
}
