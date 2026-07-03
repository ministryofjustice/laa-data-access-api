package uk.gov.justice.laa.dstew.access.usecase.getallindividuals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure.GetAllIndividualsApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.infrastructure.GetAllIndividualsIndividualGateway;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.IndividualDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getallindividuals.ApplicationClientDetailsDomainGenerator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class GetAllIndividualsUseCaseTest {

  @Mock private GetAllIndividualsIndividualGateway individualGateway;
  @Mock private GetAllIndividualsApplicationGateway applicationGateway;

  private GetAllIndividualsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetAllIndividualsUseCase(individualGateway, applicationGateway);
  }

  @Test
  void givenNoFilters_whenExecuted_thenReturnsAllIndividualsWithoutClientDetails() {
    IndividualDomain individual = DataGenerator.createDefault(IndividualDomainGenerator.class);
    Page<IndividualDomain> page = new PageImpl<>(List.of(individual));
    when(individualGateway.findAll(null, null, 1, 10)).thenReturn(page);

    GetAllIndividualsResult result =
        useCase.execute(GetAllIndividualsQuery.builder().page(1).pageSize(10).build());

    assertThat(result.individuals().getContent()).hasSize(1);
    assertThat(result.clientDetails()).isNull();
    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(10);
    verify(individualGateway).findAll(null, null, 1, 10);
    verify(applicationGateway, never()).findClientDetails(any());
  }

  @Test
  void givenApplicationIdFilter_whenExecuted_thenPassesApplicationIdToGateway() {
    UUID appId = UUID.randomUUID();
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    when(individualGateway.findAll(appId, null, 1, 10)).thenReturn(page);

    useCase.execute(
        GetAllIndividualsQuery.builder().page(1).pageSize(10).applicationId(appId).build());

    verify(individualGateway).findAll(appId, null, 1, 10);
  }

  @Test
  void givenIndividualTypeFilter_whenExecuted_thenPassesTypeStringToGateway() {
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    when(individualGateway.findAll(null, "CLIENT", 1, 10)).thenReturn(page);

    useCase.execute(
        GetAllIndividualsQuery.builder().page(1).pageSize(10).individualType("CLIENT").build());

    verify(individualGateway).findAll(null, "CLIENT", 1, 10);
  }

  @Test
  void givenBothFilters_whenExecuted_thenPassesBothToGateway() {
    UUID appId = UUID.randomUUID();
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    when(individualGateway.findAll(appId, "CLIENT", 1, 10)).thenReturn(page);

    useCase.execute(
        GetAllIndividualsQuery.builder()
            .page(1)
            .pageSize(10)
            .applicationId(appId)
            .individualType("CLIENT")
            .build());

    verify(individualGateway).findAll(appId, "CLIENT", 1, 10);
  }

  @Test
  void givenClientTypeAndClientDetailsInclude_whenExecuted_thenClientDetailsFetched() {
    UUID appId = UUID.randomUUID();
    IndividualDomain individual = DataGenerator.createDefault(IndividualDomainGenerator.class);
    Page<IndividualDomain> page = new PageImpl<>(List.of(individual));
    ApplicationClientDetailsDomain clientDetails =
        DataGenerator.createDefault(ApplicationClientDetailsDomainGenerator.class);
    when(individualGateway.findAll(appId, "CLIENT", 1, 10)).thenReturn(page);
    when(applicationGateway.findClientDetails(appId)).thenReturn(clientDetails);

    GetAllIndividualsResult result =
        useCase.execute(
            GetAllIndividualsQuery.builder()
                .page(1)
                .pageSize(10)
                .applicationId(appId)
                .individualType("CLIENT")
                .include("CLIENT_DETAILS")
                .build());

    assertThat(result.clientDetails()).isNotNull();
    assertThat(result.clientDetails()).usingRecursiveComparison().isEqualTo(clientDetails);
    verify(applicationGateway).findClientDetails(appId);
  }

  @Test
  void givenNullPage_whenExecuted_thenDefaultPageUsed() {
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    when(individualGateway.findAll(null, null, 1, 20)).thenReturn(page);

    GetAllIndividualsResult result = useCase.execute(GetAllIndividualsQuery.builder().build());

    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(20);
  }

  @Test
  void givenSecondPage_whenExecuted_thenRequestedPageIsTwo() {
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    when(individualGateway.findAll(null, null, 2, 10)).thenReturn(page);

    GetAllIndividualsResult result =
        useCase.execute(GetAllIndividualsQuery.builder().page(2).pageSize(10).build());

    assertThat(result.requestedPage()).isEqualTo(2);
    assertThat(result.requestedPageSize()).isEqualTo(10);
  }

  @Test
  void givenMaxPageSize_whenExecuted_thenRequestedPageSizeIs100() {
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    when(individualGateway.findAll(null, null, 1, 100)).thenReturn(page);

    GetAllIndividualsResult result =
        useCase.execute(GetAllIndividualsQuery.builder().page(1).pageSize(100).build());

    assertThat(result.requestedPageSize()).isEqualTo(100);
  }

  @Test
  void givenClientDetailsIncludeWithNullApplicationId_whenExecuted_thenThrowsValidationException() {
    assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () ->
                useCase.execute(
                    GetAllIndividualsQuery.builder()
                        .page(1)
                        .pageSize(10)
                        .individualType("CLIENT")
                        .include("CLIENT_DETAILS")
                        .build()))
        .satisfies(
            e ->
                assertThat(e.errors())
                    .anyMatch(
                        err ->
                            err.contains(
                                "Application ID is required when included data is CLIENT_DETAILS")));
    verify(individualGateway, never()).findAll(any(), any(), any(int.class), any(int.class));
  }

  @Test
  void givenInvalidPage_whenExecuted_thenThrowsIllegalArgumentException() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> useCase.execute(GetAllIndividualsQuery.builder().page(0).pageSize(10).build()))
        .withMessageContaining("page must be greater than or equal to 1");
    verify(individualGateway, never()).findAll(any(), any(), any(int.class), any(int.class));
  }

  @Test
  void givenInvalidPageSize_whenExecuted_thenThrowsIllegalArgumentException() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> useCase.execute(GetAllIndividualsQuery.builder().page(1).pageSize(0).build()))
        .withMessageContaining("pageSize must be greater than or equal to 1");
    verify(individualGateway, never()).findAll(any(), any(), any(int.class), any(int.class));
  }

  @Test
  void givenPageSizeExceedingMax_whenExecuted_thenThrowsIllegalArgumentException() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> useCase.execute(GetAllIndividualsQuery.builder().page(1).pageSize(101).build()))
        .withMessageContaining("pageSize cannot be more than 100");
    verify(individualGateway, never()).findAll(any(), any(), any(int.class), any(int.class));
  }

  @Test
  void givenClientTypeButNullInclude_whenExecuted_thenApplicationGatewayNotCalled() {
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    when(individualGateway.findAll(null, "CLIENT", 1, 10)).thenReturn(page);

    GetAllIndividualsResult result =
        useCase.execute(
            GetAllIndividualsQuery.builder().page(1).pageSize(10).individualType("CLIENT").build());

    assertThat(result.clientDetails()).isNull();
    verify(applicationGateway, never()).findClientDetails(any());
  }

  @Test
  void givenAppliedPreviouslyFalse_whenExecuted_thenClientDetailsHasAppliedPreviouslyFalse() {
    UUID appId = UUID.randomUUID();
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    ApplicationClientDetailsDomain clientDetails =
        DataGenerator.createDefault(
            ApplicationClientDetailsDomainGenerator.class,
            builder -> builder.appliedPreviously(false));
    when(individualGateway.findAll(appId, "CLIENT", 1, 10)).thenReturn(page);
    when(applicationGateway.findClientDetails(appId)).thenReturn(clientDetails);

    GetAllIndividualsResult result =
        useCase.execute(
            GetAllIndividualsQuery.builder()
                .page(1)
                .pageSize(10)
                .applicationId(appId)
                .individualType("CLIENT")
                .include("CLIENT_DETAILS")
                .build());

    assertThat(result.clientDetails().appliedPreviously()).isFalse();
  }

  @Test
  void givenNullApplicantFields_whenExecuted_thenApplicantSourcedClientDetailFieldsAreNull() {
    UUID appId = UUID.randomUUID();
    Page<IndividualDomain> page = new PageImpl<>(List.of());
    ApplicationClientDetailsDomain clientDetails =
        DataGenerator.createDefault(
            ApplicationClientDetailsDomainGenerator.class,
            builder ->
                builder
                    .appliedPreviously(null)
                    .correspondenceAddress(null)
                    .relationshipToInvolvedChildren(null));
    when(individualGateway.findAll(appId, "CLIENT", 1, 10)).thenReturn(page);
    when(applicationGateway.findClientDetails(appId)).thenReturn(clientDetails);

    GetAllIndividualsResult result =
        useCase.execute(
            GetAllIndividualsQuery.builder()
                .page(1)
                .pageSize(10)
                .applicationId(appId)
                .individualType("CLIENT")
                .include("CLIENT_DETAILS")
                .build());

    assertThat(result.clientDetails().appliedPreviously()).isNull();
    assertThat(result.clientDetails().correspondenceAddress()).isNull();
    assertThat(result.clientDetails().relationshipToInvolvedChildren()).isNull();
  }
}
