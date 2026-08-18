package uk.gov.justice.laa.dstew.access.controller.individual;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.query.individual.ApplicationClientDetails;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;

/** Maps Axon individual query results to the public API response. */
@Component
public class GetIndividualsResponseMapper {

  /** Maps a query result and its paging metadata to the generated response model. */
  public IndividualsResponse toResponse(FindIndividualsResult result) {
    List<IndividualResponse> individuals =
        result.individuals().stream()
            .map(individual -> toResponse(individual, result.clientDetails()))
            .toList();
    PagingResponse paging = new PagingResponse();
    paging.setPage(result.page());
    paging.setPageSize(result.pageSize());
    paging.setTotalRecords(result.totalRecords());
    paging.setItemsReturned(individuals.size());
    return new IndividualsResponse().individuals(individuals).paging(paging);
  }

  private IndividualResponse toResponse(
      ApplicationIndividual individual, ApplicationClientDetails clientDetails) {
    IndividualResponse response =
        new IndividualResponse()
            .firstName(individual.firstName())
            .lastName(individual.lastName())
            .dateOfBirth(individual.dateOfBirth())
            .details(individual.individualContent());
    if (individual.type() != null) {
      response.setType(IndividualType.valueOf(individual.type()));
    }
    if (clientDetails != null) {
      response
          .clientId(individual.individualId())
          .lastNameAtBirth(clientDetails.lastNameAtBirth())
          .previousApplicationId(clientDetails.previousApplicationId())
          .relationshipToInvolvedChildren(clientDetails.relationshipToInvolvedChildren())
          .correspondenceAddressType(clientDetails.correspondenceAddressType())
          .appliedPreviously(clientDetails.appliedPreviously())
          .correspondenceAddress(clientDetails.correspondenceAddress());
    }
    return response;
  }
}
