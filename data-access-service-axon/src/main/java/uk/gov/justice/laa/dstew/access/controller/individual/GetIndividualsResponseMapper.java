package uk.gov.justice.laa.dstew.access.controller.individual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationAddress;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;

/** Maps Axon individual query results to the public API response. */
@Component
public class GetIndividualsResponseMapper {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Maps a query result and its paging metadata to the generated response model. */
  public IndividualsResponse toResponse(FindIndividualsResult result) {
    List<IndividualResponse> individuals = new ArrayList<>();
    if (result.client() != null) {
      individuals.add(toClientResponse(result.client(), result.includeClientDetails()));
    }
    PagingResponse paging = new PagingResponse();
    paging.setPage(result.page());
    paging.setPageSize(result.pageSize());
    paging.setTotalRecords(result.totalRecords());
    paging.setItemsReturned(individuals.size());
    return new IndividualsResponse().individuals(individuals).paging(paging);
  }

  private IndividualResponse toClientResponse(
      ApplicationClient client, boolean includeClientDetails) {
    IndividualResponse response =
        new IndividualResponse()
            .firstName(client.getFirstName())
            .lastName(client.getLastName())
            .dateOfBirth(
                client.getDateOfBirth() != null
                    ? java.time.LocalDate.parse(client.getDateOfBirth())
                    : null)
            .type(IndividualType.CLIENT);
    if (includeClientDetails) {
      response
          .lastNameAtBirth(client.getLastNameAtBirth())
          .previousApplicationId(client.getPreviousApplicationId())
          .relationshipToInvolvedChildren(client.getRelationshipToInvolvedChildren())
          .appliedPreviously(client.getAppliedPreviously())
          .correspondenceAddress(toAddressMaps(client.getAddresses()));
    }
    return response;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> toAddressMaps(List<ApplicationAddress> addresses) {
    if (addresses == null) {
      return null;
    }
    return addresses.stream()
        .map(addr -> (Map<String, Object>) objectMapper.convertValue(addr, Map.class))
        .toList();
  }
}
