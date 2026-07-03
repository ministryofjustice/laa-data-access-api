package uk.gov.justice.laa.dstew.access.controller.individual;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.GetAllIndividualsResult;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.IndividualDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getallindividuals.ApplicationClientDetailsDomainGenerator;

class GetAllIndividualsResponseMapperTest {

  private GetAllIndividualsResponseMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetAllIndividualsResponseMapper();
  }

  @Test
  void givenBasicIndividuals_whenToGetAllIndividualsResponse_thenBasicFieldsMapped() {
    IndividualDomain individual = DataGenerator.createDefault(IndividualDomainGenerator.class);
    Page<IndividualDomain> page = new PageImpl<>(List.of(individual), PageRequest.of(0, 10), 1);
    GetAllIndividualsResult result =
        GetAllIndividualsResult.builder()
            .individuals(page)
            .requestedPage(1)
            .requestedPageSize(10)
            .clientDetails(null)
            .build();

    ResponseEntity<IndividualsResponse> responseEntity = mapper.toGetAllIndividualsResponse(result);

    assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
    IndividualsResponse body = responseEntity.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getIndividuals()).hasSize(1);
    IndividualResponse individualResponse = body.getIndividuals().getFirst();
    assertThat(individualResponse.getFirstName()).isEqualTo(individual.firstName());
    assertThat(individualResponse.getLastName()).isEqualTo(individual.lastName());
    assertThat(individualResponse.getDateOfBirth()).isEqualTo(individual.dateOfBirth());
    assertThat(individualResponse.getDetails()).isEqualTo(individual.individualContent());
    assertThat(individualResponse.getType()).isEqualTo(IndividualType.valueOf(individual.type()));
    assertThat(individualResponse.getClientId()).isNull();
    assertThat(individualResponse.getLastNameAtBirth()).isNull();
    assertThat(individualResponse.getPreviousApplicationId()).isNull();
    assertThat(individualResponse.getRelationshipToInvolvedChildren()).isNull();
    assertThat(individualResponse.getCorrespondenceAddressType()).isNull();
    assertThat(individualResponse.getAppliedPreviously()).isNull();
    assertThat(individualResponse.getCorrespondenceAddress()).isNullOrEmpty();
    assertThat(body.getPaging().getPage()).isEqualTo(1);
    assertThat(body.getPaging().getPageSize()).isEqualTo(10);
    assertThat(body.getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(body.getPaging().getItemsReturned()).isEqualTo(1);
  }

  @Test
  void givenExtendedIndividuals_whenToGetAllIndividualsResponse_thenExtendedFieldsMapped() {
    UUID individualId = UUID.randomUUID();
    IndividualDomain individual =
        DataGenerator.createDefault(
            IndividualDomainGenerator.class, builder -> builder.id(individualId));
    var clientDetails = DataGenerator.createDefault(ApplicationClientDetailsDomainGenerator.class);
    Page<IndividualDomain> page = new PageImpl<>(List.of(individual), PageRequest.of(0, 10), 1);
    GetAllIndividualsResult result =
        GetAllIndividualsResult.builder()
            .individuals(page)
            .requestedPage(1)
            .requestedPageSize(10)
            .clientDetails(clientDetails)
            .build();

    ResponseEntity<IndividualsResponse> responseEntity = mapper.toGetAllIndividualsResponse(result);

    IndividualResponse individualResponse = responseEntity.getBody().getIndividuals().getFirst();
    assertThat(individualResponse.getClientId()).isEqualTo(individualId);
    assertThat(individualResponse.getLastNameAtBirth()).isEqualTo(clientDetails.lastNameAtBirth());
    assertThat(individualResponse.getPreviousApplicationId())
        .isEqualTo(clientDetails.previousApplicationId());
    assertThat(individualResponse.getRelationshipToInvolvedChildren())
        .isEqualTo(clientDetails.relationshipToInvolvedChildren());
    assertThat(individualResponse.getCorrespondenceAddressType())
        .isEqualTo(clientDetails.correspondenceAddressType());
    assertThat(individualResponse.getAppliedPreviously())
        .isEqualTo(clientDetails.appliedPreviously());
    assertThat(individualResponse.getCorrespondenceAddress())
        .isEqualTo(clientDetails.correspondenceAddress());
  }

  @Test
  void givenNullAppliedPreviously_whenToGetAllIndividualsResponse_thenAppliedPreviouslyIsNull() {
    IndividualDomain individual = DataGenerator.createDefault(IndividualDomainGenerator.class);
    var clientDetails =
        DataGenerator.createDefault(
            ApplicationClientDetailsDomainGenerator.class,
            builder -> builder.appliedPreviously(null));
    Page<IndividualDomain> page = new PageImpl<>(List.of(individual));
    GetAllIndividualsResult result =
        GetAllIndividualsResult.builder()
            .individuals(page)
            .requestedPage(1)
            .requestedPageSize(10)
            .clientDetails(clientDetails)
            .build();

    IndividualResponse individualResponse =
        mapper.toGetAllIndividualsResponse(result).getBody().getIndividuals().getFirst();

    assertThat(individualResponse.getAppliedPreviously()).isNull();
  }

  @Test
  void
      givenNullCorrespondenceAddress_whenToGetAllIndividualsResponse_thenCorrespondenceAddressIsNull() {
    IndividualDomain individual = DataGenerator.createDefault(IndividualDomainGenerator.class);
    var clientDetails =
        DataGenerator.createDefault(
            ApplicationClientDetailsDomainGenerator.class,
            builder -> builder.correspondenceAddress(null));
    Page<IndividualDomain> page = new PageImpl<>(List.of(individual));
    GetAllIndividualsResult result =
        GetAllIndividualsResult.builder()
            .individuals(page)
            .requestedPage(1)
            .requestedPageSize(10)
            .clientDetails(clientDetails)
            .build();

    IndividualResponse individualResponse =
        mapper.toGetAllIndividualsResponse(result).getBody().getIndividuals().getFirst();

    assertThat(individualResponse.getCorrespondenceAddress()).isNull();
  }

  @Test
  void givenEmptyIndividualsPage_whenToGetAllIndividualsResponse_thenEmptyListWithCorrectPaging() {
    Page<IndividualDomain> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
    GetAllIndividualsResult result =
        GetAllIndividualsResult.builder()
            .individuals(page)
            .requestedPage(1)
            .requestedPageSize(20)
            .clientDetails(null)
            .build();

    ResponseEntity<IndividualsResponse> responseEntity = mapper.toGetAllIndividualsResponse(result);

    IndividualsResponse body = responseEntity.getBody();
    assertThat(body.getIndividuals()).isEmpty();
    assertThat(body.getPaging().getPage()).isEqualTo(1);
    assertThat(body.getPaging().getPageSize()).isEqualTo(20);
    assertThat(body.getPaging().getTotalRecords()).isZero();
    assertThat(body.getPaging().getItemsReturned()).isZero();
  }

  @Test
  void givenMultipleIndividuals_whenToGetAllIndividualsResponse_thenPagingMetadataCorrect() {
    List<IndividualDomain> individuals =
        List.of(
            DataGenerator.createDefault(IndividualDomainGenerator.class),
            DataGenerator.createDefault(IndividualDomainGenerator.class));
    Page<IndividualDomain> page = new PageImpl<>(individuals, PageRequest.of(0, 10), 25);
    GetAllIndividualsResult result =
        GetAllIndividualsResult.builder()
            .individuals(page)
            .requestedPage(1)
            .requestedPageSize(10)
            .clientDetails(null)
            .build();

    ResponseEntity<IndividualsResponse> responseEntity = mapper.toGetAllIndividualsResponse(result);

    IndividualsResponse body = responseEntity.getBody();
    assertThat(body.getIndividuals()).hasSize(2);
    assertThat(body.getPaging().getTotalRecords()).isEqualTo(25);
    assertThat(body.getPaging().getItemsReturned()).isEqualTo(2);
    assertThat(body.getPaging().getPage()).isEqualTo(1);
    assertThat(body.getPaging().getPageSize()).isEqualTo(10);
  }

  @Test
  void givenNullIndividualType_whenToGetAllIndividualsResponse_thenTypeIsNull() {
    IndividualDomain individual =
        DataGenerator.createDefault(IndividualDomainGenerator.class, builder -> builder.type(null));
    Page<IndividualDomain> page = new PageImpl<>(List.of(individual));
    GetAllIndividualsResult result =
        GetAllIndividualsResult.builder()
            .individuals(page)
            .requestedPage(1)
            .requestedPageSize(10)
            .clientDetails(null)
            .build();

    IndividualResponse individualResponse =
        mapper.toGetAllIndividualsResponse(result).getBody().getIndividuals().getFirst();

    assertThat(individualResponse.getType()).isNull();
  }
}
