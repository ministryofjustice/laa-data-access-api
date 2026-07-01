package uk.gov.justice.laa.dstew.access.controller.individual;

import java.util.List;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.GetAllIndividualsResult;

/** Maps {@link GetAllIndividualsResult} to the API-facing {@link IndividualsResponse}. */
public class GetAllIndividualsResponseMapper {

  /**
   * Converts the use-case result to a response entity, including paging envelope.
   *
   * @param result the use-case result
   * @return response entity containing the individuals response
   */
  public ResponseEntity<IndividualsResponse> toGetAllIndividualsResponse(
      GetAllIndividualsResult result) {
    List<IndividualResponse> individualResponses =
        result.individuals().getContent().stream()
            .map(individual -> toIndividualResponse(individual, result.clientDetails()))
            .toList();

    PagingResponse pagingResponse = new PagingResponse();
    pagingResponse.setPage(result.requestedPage());
    pagingResponse.pageSize(result.requestedPageSize());
    pagingResponse.totalRecords((int) result.individuals().getTotalElements());
    pagingResponse.itemsReturned(individualResponses.size());

    IndividualsResponse response = new IndividualsResponse();
    response.setIndividuals(individualResponses);
    response.setPaging(pagingResponse);
    return ResponseEntity.ok(response);
  }

  private IndividualResponse toIndividualResponse(
      IndividualDomain individual, ApplicationClientDetailsDomain clientDetails) {
    IndividualResponse dto = new IndividualResponse();
    dto.setFirstName(individual.firstName());
    dto.setLastName(individual.lastName());
    dto.setDateOfBirth(individual.dateOfBirth());
    dto.setDetails(individual.individualContent());
    if (individual.type() != null) {
      dto.setType(IndividualType.valueOf(individual.type()));
    }
    if (clientDetails != null) {
      dto.setClientId(individual.id());
      dto.setLastNameAtBirth(clientDetails.lastNameAtBirth());
      dto.setPreviousApplicationId(clientDetails.previousApplicationId());
      dto.setRelationshipToInvolvedChildren(clientDetails.relationshipToInvolvedChildren());
      dto.setCorrespondenceAddressType(clientDetails.correspondenceAddressType());
      dto.setAppliedPreviously(clientDetails.appliedPreviously());
      dto.setCorrespondenceAddress(clientDetails.correspondenceAddress());
    }
    return dto;
  }
}
