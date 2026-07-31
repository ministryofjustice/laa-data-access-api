package uk.gov.justice.laa.dstew.access.controller.individual;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.query.individual.ApplicationClientDetails;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;

class GetIndividualsResponseMapperTest {

  private final GetIndividualsResponseMapper mapper = new GetIndividualsResponseMapper();

  @Test
  void givenIndividualAndClientDetails_whenMapped_thenPopulatesResponseAndPaging() {
    UUID individualId = UUID.randomUUID();
    ApplicationIndividual individual =
        new ApplicationIndividual(
            individualId,
            "Ada",
            "Lovelace",
            LocalDate.of(1815, 12, 10),
            Map.of("preferredName", "Ada"),
            "CLIENT");
    ApplicationClientDetails clientDetails =
        new ApplicationClientDetails(
            "Byron", "previous-id", "MOTHER", "HOME", true, List.of(Map.of("line1", "London")));

    IndividualsResponse response =
        mapper.toResponse(new FindIndividualsResult(List.of(individual), 2, 5, 7, clientDetails));

    IndividualResponse mapped = response.getIndividuals().get(0);
    assertThat(mapped.getClientId()).isEqualTo(individualId);
    assertThat(mapped.getFirstName()).isEqualTo("Ada");
    assertThat(mapped.getType().name()).isEqualTo("CLIENT");
    assertThat(mapped.getLastNameAtBirth()).isEqualTo("Byron");
    assertThat(mapped.getAppliedPreviously()).isTrue();
    assertThat(response.getPaging().getPage()).isEqualTo(2);
    assertThat(response.getPaging().getPageSize()).isEqualTo(5);
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(7);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(1);
  }
}
