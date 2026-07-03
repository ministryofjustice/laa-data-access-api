package uk.gov.justice.laa.dstew.access.controller.individual;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.api.IndividualsApi;
import uk.gov.justice.laa.dstew.access.model.IncludedAdditionalData;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.GetAllIndividualsUseCase;

/** Controller for handling /api/v0/individuals requests. */
@RequiredArgsConstructor
@RestController
@ExcludeFromGeneratedCodeCoverage
public class IndividualsController implements IndividualsApi {

  private final GetAllIndividualsUseCase getAllIndividualsUseCase;
  private final GetAllIndividualsResponseMapper getAllIndividualsResponseMapper;
  private final GetAllIndividualsQueryMapper getAllIndividualsQueryMapper;

  /**
   * Retrieves a paginated list of individuals.
   *
   * @param serviceName the service name header
   * @param include the additional data to be included in response
   * @param page the page number (1-based), may be null for default
   * @param pageSize the number of items per page, may be null for default
   * @param applicationId the application UUID to filter by (nullable)
   * @param type the individual type to filter by (nullable)
   * @return a {@link ResponseEntity} containing the {@link IndividualsResponse} with paging info
   */
  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<IndividualsResponse> getIndividuals(
      ServiceName serviceName,
      IncludedAdditionalData include,
      Integer page,
      Integer pageSize,
      UUID applicationId,
      IndividualType type) {
    return getAllIndividualsResponseMapper.toGetAllIndividualsResponse(
        getAllIndividualsUseCase.execute(
            getAllIndividualsQueryMapper.toQuery(page, pageSize, applicationId, type, include)));
  }
}
