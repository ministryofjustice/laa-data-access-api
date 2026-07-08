package uk.gov.justice.laa.dstew.access.controller.individual;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.IncludedAdditionalData;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.GetAllIndividualsQuery;

/** Maps HTTP request parameters to {@link GetAllIndividualsQuery}. */
public class GetAllIndividualsQueryMapper {

  /**
   * Converts HTTP request parameters to a {@link GetAllIndividualsQuery}.
   *
   * @param page the page number (1-based), may be null for default
   * @param pageSize the number of items per page, may be null for default
   * @param applicationId the application UUID to filter by (nullable)
   * @param type the individual type to filter by (nullable)
   * @param include the additional data to include (nullable)
   * @return the query record
   */
  public GetAllIndividualsQuery toQuery(
      Integer page,
      Integer pageSize,
      UUID applicationId,
      IndividualType type,
      IncludedAdditionalData include) {
    return GetAllIndividualsQuery.builder()
        .page(page)
        .pageSize(pageSize)
        .applicationId(applicationId)
        .individualType(type == null ? null : type.name())
        .include(include == null ? null : include.name())
        .build();
  }
}
