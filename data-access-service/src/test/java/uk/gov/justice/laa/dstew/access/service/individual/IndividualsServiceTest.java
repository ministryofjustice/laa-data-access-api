package uk.gov.justice.laa.dstew.access.service.individual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.ApplicationApplicant;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.IncludedAdditionalData;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class IndividualsServiceTest extends BaseServiceTest {

  @Autowired
  private IndividualsService serviceUnderTest;

  @Test
  public void givenIndividuals_whenGetIndividuals_thenReturnExtendedIndividuals() {
    // given
    IndividualEntity expectedIndividual = DataGenerator.createDefault(IndividualEntityGenerator.class,
            builder -> builder.id(UUID.randomUUID()));
    List<IndividualEntity> expectedIndividuals = List.of(expectedIndividual);
    Page<IndividualEntity> pageResult = new PageImpl<>(expectedIndividuals);

    ApplicationEntity application = DataGenerator.createDefault(ApplicationEntityGenerator.class,
      builder -> builder.id(UUID.randomUUID()));
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);
    when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

    // when
    List<IndividualResponse> actualIndividualResponses = serviceUnderTest.getIndividuals(
            1, 10,
                    application.getId(), IndividualType.CLIENT, IncludedAdditionalData.CLIENT_DETAILS)
            .page().stream().toList();

    // then
    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertExtendedIndividualEqual(expectedIndividuals.getFirst(),
                                  actualIndividualResponses.getFirst(),
                                  MapperUtil.getObjectMapper()
                                    .convertValue(application.getApplicationContent(), ApplicationContent.class)
                                  );
  }

  @Test
  @SuppressWarnings("unchecked")
  public void givenAppliedPreviouslyFalse_whenGetIndividuals_thenReturnAppliedPreviouslyFalse() {
    // given
    IndividualEntity expectedIndividual = DataGenerator.createDefault(IndividualEntityGenerator.class,
            builder -> builder.id(UUID.randomUUID()));
    Page<IndividualEntity> pageResult = new PageImpl<>(List.of(expectedIndividual));

    ApplicationEntity application = createApplicationWithAppliedPreviously(false);
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);
    when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

    // when
    List<IndividualResponse> actualIndividuals = serviceUnderTest.getIndividuals(
            1, 10, application.getId(), IndividualType.CLIENT, IncludedAdditionalData.CLIENT_DETAILS)
            .page().stream().toList();

    // then
    assertThat(actualIndividuals.getFirst().getAppliedPreviously()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void givenNullApplicant_whenGetIndividuals_thenReturnAppliedPreviouslyNull() {
    // given
    IndividualEntity expectedIndividual = DataGenerator.createDefault(IndividualEntityGenerator.class,
            builder -> builder.id(UUID.randomUUID()));
    Page<IndividualEntity> pageResult = new PageImpl<>(List.of(expectedIndividual));

    ApplicationEntity application = createApplicationWithAppliedPreviously(null);
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);
    when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

    // when
    List<IndividualResponse> actualIndividuals = serviceUnderTest.getIndividuals(
            1, 10, application.getId(), IndividualType.CLIENT, IncludedAdditionalData.CLIENT_DETAILS)
            .page().stream().toList();

    // then
    assertThat(actualIndividuals.getFirst().getAppliedPreviously()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void givenApplicantWithAddresses_whenGetIndividuals_thenReturnCorrespondenceAddress() {
    // given
    IndividualEntity expectedIndividual = DataGenerator.createDefault(IndividualEntityGenerator.class,
            builder -> builder.id(UUID.randomUUID()));
    Page<IndividualEntity> pageResult = new PageImpl<>(List.of(expectedIndividual));

    List<Map<String, Object>> addresses = List.of(
        Map.of("line1", "address 1"),
        Map.of("line1", "address 2")
    );
    ApplicationEntity application = createApplicationWithAddresses(addresses);
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);
    when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

    // when
    List<IndividualResponse> actualIndividuals = serviceUnderTest.getIndividuals(
            1, 10, application.getId(), IndividualType.CLIENT, IncludedAdditionalData.CLIENT_DETAILS)
            .page().stream().toList();

    // then
    assertThat(actualIndividuals.getFirst().getCorrespondenceAddress()).hasSize(2);
    assertThat(actualIndividuals.getFirst().getCorrespondenceAddress()).isEqualTo(addresses);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 10})
  public void givenIndividuals_whenGetIndividuals_thenReturnIndividuals(int count) {
    // given
    List<IndividualEntity> expectedIndividuals = DataGenerator.createMultipleDefault(IndividualEntityGenerator.class, count);
    Page<IndividualEntity> pageResult = new PageImpl<>(expectedIndividuals);

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // when
    List<IndividualResponse>
        actualIndividualResponses = serviceUnderTest.getIndividuals(1, 10, null, null, null).page().stream().toList();

    // then
    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertIndividualListsEqual(actualIndividualResponses, expectedIndividuals);
  }

  @Test
  public void givenDefaultPagination_whenGetIndividuals_thenReturnIndividuals() {
    // given
    List<IndividualEntity> expectedIndividuals = DataGenerator.createMultipleDefault(IndividualEntityGenerator.class, 5);
    Page<IndividualEntity> pageResult = new PageImpl<>(expectedIndividuals);

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // when
    PaginatedResult<IndividualResponse> result = serviceUnderTest.getIndividuals(null, null, null, null, null);

    // then
    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

    assertThat(result.requestedPage()).isEqualTo(1); // one-based
    assertThat(result.requestedPageSize()).isEqualTo(20); // default
    assertThat(result.page().getContent()).hasSize(5);
  }

  @Test
  public void givenSecondPage_whenGetIndividuals_thenReturnIndividuals() {
    // given
    List<IndividualEntity> expectedIndividuals = DataGenerator.createMultipleDefault(IndividualEntityGenerator.class, 5);
    Page<IndividualEntity> pageResult = new PageImpl<>(expectedIndividuals);

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // when
    PaginatedResult<IndividualResponse> result = serviceUnderTest.getIndividuals(2, 10, null, null, null);

    // then
    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

    assertThat(result.requestedPage()).isEqualTo(2); // one-based
    assertThat(result.requestedPageSize()).isEqualTo(10);
    assertThat(result.page().getContent()).hasSize(5);
  }

  @Test
  public void givenInvalidPage_whenGetIndividuals_thenThrowException() {
    // given
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when/then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> serviceUnderTest.getIndividuals(0, 10, null, null, null))
        .withMessageContaining("page must be greater than or equal to 1");

    verify(individualRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  public void givenInvalidPageSize_whenGetIndividuals_thenThrowException() {
    // given
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when/then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> serviceUnderTest.getIndividuals(1, 0, null, null, null))
        .withMessageContaining("pageSize must be greater than or equal to 1");

    verify(individualRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  public void givenPageSizeExceedingMax_whenGetIndividuals_thenThrowException() {
    // given
    setSecurityContext(TestConstants.Roles.CASEWORKER);

    // when/then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> serviceUnderTest.getIndividuals(1, 101, null, null, null))
        .withMessageContaining("pageSize cannot be more than 100");

    verify(individualRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  public void givenMaximumPageSize_whenGetIndividuals_thenReturnIndividuals() {
    // given
    List<IndividualEntity> expectedIndividuals = DataGenerator.createMultipleDefault(IndividualEntityGenerator.class, 5);
    Page<IndividualEntity> pageResult = new PageImpl<>(expectedIndividuals);

    setSecurityContext(TestConstants.Roles.CASEWORKER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // when
    PaginatedResult<IndividualResponse> result = serviceUnderTest.getIndividuals(1, 100, null, null, null);

    // then
    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

    assertThat(result.requestedPageSize()).isEqualTo(100);
    assertThat(result.page().getContent()).hasSize(5);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void noFilters_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
    setSecurityContext(TestConstants.Roles.CASEWORKER);
    IndividualEntity entity = DataGenerator.createDefault(IndividualEntityGenerator.class);
    Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

    PaginatedResult<IndividualResponse> result = serviceUnderTest.getIndividuals(1, 10, null, null, null);

    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertThat(result.page()).hasSize(1);
    assertThat(result.page().getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
    assertThat(result.page().getContent().getFirst().getLastName()).isEqualTo(entity.getLastName());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void applicationIdProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
    setSecurityContext(TestConstants.Roles.CASEWORKER);
    UUID appId = UUID.randomUUID();
    IndividualEntity entity = DataGenerator.createDefault(IndividualEntityGenerator.class);
    Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

    PaginatedResult<IndividualResponse> result = serviceUnderTest.getIndividuals(1, 10, appId, null, null);

    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertThat(result.page()).hasSize(1);
    assertThat(result.page().getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void individualTypeProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
    setSecurityContext(TestConstants.Roles.CASEWORKER);
    IndividualType type = IndividualType.CLIENT;
    IndividualEntity entity = DataGenerator.createDefault(IndividualEntityGenerator.class);
    Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

    PaginatedResult<IndividualResponse> result = serviceUnderTest.getIndividuals(1, 10, null, type, null);

    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertThat(result.page()).hasSize(1);
    assertThat(result.page().getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void bothFiltersProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
    setSecurityContext(TestConstants.Roles.CASEWORKER);
    UUID appId = UUID.randomUUID();
    IndividualType type = IndividualType.CLIENT;
    IndividualEntity entity = DataGenerator.createDefault(IndividualEntityGenerator.class);
    Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

    PaginatedResult<IndividualResponse> result = serviceUnderTest.getIndividuals(1, 10, appId, type, null);

    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertThat(result.page()).hasSize(1);
    assertThat(result.page().getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
  }

  private void assertIndividualListsEqual(List<IndividualResponse> actualList, List<IndividualEntity> expectedList) {
    assertThat(actualList).hasSameSizeAs(expectedList);

    for (IndividualEntity expected : expectedList) {
      boolean match = actualList.stream()
          .anyMatch(actual -> {
            try {
              assertIndividualEqual(expected, actual);
              return true;
            } catch (AssertionError e) {
              return false;
            }
          });
      assertThat(match)
          .as("No matching Individual found for expected: " + expected)
          .isTrue();
    }
  }

  private void assertIndividualEqual(IndividualEntity expected, IndividualResponse actual) {
    assertThat(actual.getFirstName()).isEqualTo(expected.getFirstName());
    assertThat(actual.getLastName()).isEqualTo(expected.getLastName());
    assertThat(actual.getDateOfBirth()).isEqualTo(expected.getDateOfBirth());
    assertThat(actual.getType()).isEqualTo(expected.getType());
    assertThat(actual.getDetails()).isEqualTo(expected.getIndividualContent());
  }

  private void assertExtendedIndividualEqual(IndividualEntity expected,
                                             IndividualResponse actual,
                                             ApplicationContent applicationContent) {
    assertIndividualEqual(expected, actual);
    assertThat(actual.getClientId()).isEqualTo(expected.getId());
    assertThat(actual.getLastNameAtBirth()).isEqualTo(applicationContent.getLastNameAtBirth());
    assertThat(actual.getPreviousApplicationId()).isEqualTo(applicationContent.getPreviousApplicationId());
    assertThat(actual.getRelationshipToChildren()).isEqualTo(applicationContent.getRelationshipToChildren());
    assertThat(actual.getCorrespondenceAddressType()).isEqualTo(applicationContent.getCorrespondenceAddressType());
    var expectedAppliedPreviously = Optional.ofNullable(applicationContent.getApplicant())
        .map(ApplicationApplicant::getAppliedPreviously)
        .orElse(null);
    assertThat(actual.getAppliedPreviously()).isEqualTo(expectedAppliedPreviously);
  }

  @SuppressWarnings("unchecked")
  private ApplicationEntity createApplicationWithAppliedPreviously(Boolean appliedPreviously) {
    ApplicationApplicant applicant = appliedPreviously != null
        ? ApplicationApplicant.builder().appliedPreviously(appliedPreviously).build()
        : null;
    return DataGenerator.createDefault(ApplicationEntityGenerator.class,
        builder -> builder.id(UUID.randomUUID())
            .applicationContent(MapperUtil.getObjectMapper().convertValue(
                ApplicationContent.builder().applicant(applicant).build(),
                Map.class)));
  }

  @SuppressWarnings("unchecked")
  private ApplicationEntity createApplicationWithAddresses(List<Map<String, Object>> addresses) {
    ApplicationApplicant applicant = ApplicationApplicant.builder()
        .addresses(addresses)
        .build();
    return DataGenerator.createDefault(ApplicationEntityGenerator.class,
        builder -> builder.id(UUID.randomUUID())
            .applicationContent(MapperUtil.getObjectMapper().convertValue(
                ApplicationContent.builder().applicant(applicant).build(),
                Map.class)));
  }
}
