package uk.gov.justice.laa.dstew.access.controller.individual;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationAddress;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;

class GetIndividualsResponseMapperTest {

  private final GetIndividualsResponseMapper mapper = new GetIndividualsResponseMapper();

  @Test
  void givenClientAndClientDetails_whenMapped_thenPopulatesResponseAndPaging() {
    ApplicationClient client =
        ApplicationClient.builder()
            .firstName("Ada")
            .lastName("Lovelace")
            .dateOfBirth(LocalDate.of(1815, 12, 10))
            .lastNameAtBirth("Byron")
            .previousApplicationId("previous-id")
            .relationshipToInvolvedChildren("MOTHER")
            .appliedPreviously(true)
            .addresses(
                List.of(
                    ApplicationAddress.builder()
                        .addressLineOne("London")
                        .countryCode("GBR")
                        .build()))
            .build();

    IndividualsResponse response =
        mapper.toResponse(new FindIndividualsResult(client, 2, 5, 1, true));

    IndividualResponse mapped = response.getIndividuals().get(0);
    assertThat(mapped.getClientId()).isNull();
    assertThat(mapped.getFirstName()).isEqualTo("Ada");
    assertThat(mapped.getType().name()).isEqualTo("CLIENT");
    assertThat(mapped.getDateOfBirth().toString()).isEqualTo("1815-12-10");
    assertThat(mapped.getLastNameAtBirth()).isEqualTo("Byron");
    assertThat(mapped.getAppliedPreviously()).isTrue();
    assertThat(response.getPaging().getPage()).isEqualTo(2);
    assertThat(response.getPaging().getPageSize()).isEqualTo(5);
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(1);
  }

  @Test
  void givenClientWithIncludeClientDetailsFalse_whenMapped_thenExcludesPrivateFields() {
    ApplicationClient client =
        ApplicationClient.builder()
            .firstName("Ada")
            .lastName("Lovelace")
            .dateOfBirth(LocalDate.of(1815, 12, 10))
            .lastNameAtBirth("Byron")
            .appliedPreviously(true)
            .build();

    IndividualsResponse response =
        mapper.toResponse(new FindIndividualsResult(client, 1, 10, 1, false));

    IndividualResponse mapped = response.getIndividuals().get(0);
    assertThat(mapped.getFirstName()).isEqualTo("Ada");
    assertThat(mapped.getLastName()).isEqualTo("Lovelace");
    assertThat(mapped.getType().name()).isEqualTo("CLIENT");
    assertThat(mapped.getLastNameAtBirth()).isNull();
    assertThat(mapped.getAppliedPreviously()).isNull();
    assertThat(mapped.getCorrespondenceAddress()).isNullOrEmpty();
  }

  @Test
  void givenNullClient_whenMapped_thenReturnsEmptyIndividuals() {
    IndividualsResponse response =
        mapper.toResponse(new FindIndividualsResult(null, 1, 10, 0, false));

    assertThat(response.getIndividuals()).isEmpty();
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(0);
  }
}
