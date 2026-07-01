package uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;

/** Gateway interface for paginated individual queries. */
public interface GetAllIndividualsIndividualGateway {

  /**
   * Returns a page of individuals matching the given filters.
   *
   * @param applicationId optional application UUID filter (null means no filter)
   * @param individualType optional individual type filter as String (null means no filter)
   * @param page one-based page number
   * @param pageSize page size
   * @return page of matching individuals as domain records
   */
  Page<IndividualDomain> findAll(UUID applicationId, String individualType, int page, int pageSize);
}
