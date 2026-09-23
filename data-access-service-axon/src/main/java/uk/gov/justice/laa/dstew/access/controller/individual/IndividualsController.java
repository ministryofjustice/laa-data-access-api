package uk.gov.justice.laa.dstew.access.controller.individual;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.IndividualsApi;
import uk.gov.justice.laa.dstew.access.model.IncludedAdditionalData;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.usecase.individuals.GetAllIndividualsUseCase;

/** HTTP query adapter for individual searches. */
@RestController
public class IndividualsController implements IndividualsApi {

  private final GetAllIndividualsUseCase getAllIndividualsUseCase;
  private final GetIndividualsResponseMapper responseMapper;

  public IndividualsController(
      GetAllIndividualsUseCase getAllIndividualsUseCase,
      GetIndividualsResponseMapper responseMapper) {
    this.getAllIndividualsUseCase = getAllIndividualsUseCase;
    this.responseMapper = responseMapper;
  }

  /** Returns a filtered, paginated list of individuals from current application data. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<IndividualsResponse> getIndividuals(
      ServiceName serviceName,
      IncludedAdditionalData include,
      Integer page,
      Integer pageSize,
      UUID applicationId,
      IndividualType type) {
    FindIndividualsResult result =
        getAllIndividualsUseCase.execute(
            new FindIndividualsQuery(
                applicationId,
                type == null ? null : type.name(),
                include == IncludedAdditionalData.CLIENT_DETAILS,
                page,
                pageSize));
    return ResponseEntity.ok(responseMapper.toResponse(result));
  }
}
