package uk.gov.justice.laa.dstew.access.service.individual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class IndividualsServiceTest extends BaseServiceTest {

  @Autowired
  private IndividualsService serviceUnderTest;

  @ParameterizedTest
  @ValueSource(ints = {0, 10})
  public void givenIndividuals_whenGetIndividuals_thenReturnIndividuals(int count) {
    // given
    List<IndividualEntity> expectedIndividuals = DataGenerator.createMultipleDefault(IndividualEntityGenerator.class, count);
    Page<IndividualEntity> pageResult = new PageImpl<>(expectedIndividuals);

    setSecurityContext(TestConstants.Roles.READER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // when
    List<Individual> actualIndividuals = serviceUnderTest.getIndividuals(1, 10, null, null).page().stream().toList();

    // then
    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertIndividualListsEqual(actualIndividuals, expectedIndividuals);
  }

  @Test
  public void givenDefaultPagination_whenGetIndividuals_thenReturnIndividuals() {
    // given
    List<IndividualEntity> expectedIndividuals = DataGenerator.createMultipleDefault(IndividualEntityGenerator.class, 5);
    Page<IndividualEntity> pageResult = new PageImpl<>(expectedIndividuals);

    setSecurityContext(TestConstants.Roles.READER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // when
    PaginatedResult<Individual> result = serviceUnderTest.getIndividuals(null, null, null, null);

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

    setSecurityContext(TestConstants.Roles.READER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // when
    PaginatedResult<Individual> result = serviceUnderTest.getIndividuals(2, 10, null, null);

    // then
    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

    assertThat(result.requestedPage()).isEqualTo(2); // one-based
    assertThat(result.requestedPageSize()).isEqualTo(10);
    assertThat(result.page().getContent()).hasSize(5);
  }

  @Test
  public void givenInvalidPage_whenGetIndividuals_thenThrowException() {
    // given
    setSecurityContext(TestConstants.Roles.READER);

    // when/then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> serviceUnderTest.getIndividuals(0, 10, null, null))
        .withMessageContaining("page must be greater than or equal to 1");

    verify(individualRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  public void givenInvalidPageSize_whenGetIndividuals_thenThrowException() {
    // given
    setSecurityContext(TestConstants.Roles.READER);

    // when/then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> serviceUnderTest.getIndividuals(1, 0, null, null))
        .withMessageContaining("pageSize must be greater than or equal to 1");

    verify(individualRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  public void givenPageSizeExceedingMax_whenGetIndividuals_thenThrowException() {
    // given
    setSecurityContext(TestConstants.Roles.READER);

    // when/then
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> serviceUnderTest.getIndividuals(1, 101, null, null))
        .withMessageContaining("pageSize cannot be more than 100");

    verify(individualRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  public void givenMaximumPageSize_whenGetIndividuals_thenReturnIndividuals() {
    // given
    List<IndividualEntity> expectedIndividuals = DataGenerator.createMultipleDefault(IndividualEntityGenerator.class, 5);
    Page<IndividualEntity> pageResult = new PageImpl<>(expectedIndividuals);

    setSecurityContext(TestConstants.Roles.READER);

    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

    // when
    PaginatedResult<Individual> result = serviceUnderTest.getIndividuals(1, 100, null, null);

    // then
    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

    assertThat(result.requestedPageSize()).isEqualTo(100);
    assertThat(result.page().getContent()).hasSize(5);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void noFilters_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
    setSecurityContext(TestConstants.Roles.READER);
    IndividualEntity entity = individualEntityFactory.createDefault();
    Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

    PaginatedResult<Individual> result = serviceUnderTest.getIndividuals(1, 10, null, null);

    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertThat(result.page()).hasSize(1);
    assertThat(result.page().getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
    assertThat(result.page().getContent().getFirst().getLastName()).isEqualTo(entity.getLastName());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void applicationIdProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
    setSecurityContext(TestConstants.Roles.READER);
    UUID appId = UUID.randomUUID();
    IndividualEntity entity = individualEntityFactory.createDefault();
    Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

    PaginatedResult<Individual> result = serviceUnderTest.getIndividuals(1, 10, appId, null);

    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertThat(result.page()).hasSize(1);
    assertThat(result.page().getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void individualTypeProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
    setSecurityContext(TestConstants.Roles.READER);
    IndividualType type = IndividualType.CLIENT;
    IndividualEntity entity = individualEntityFactory.createDefault();
    Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

    PaginatedResult<Individual> result = serviceUnderTest.getIndividuals(1, 10, null, type);

    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertThat(result.page()).hasSize(1);
    assertThat(result.page().getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void bothFiltersProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
    setSecurityContext(TestConstants.Roles.READER);
    UUID appId = UUID.randomUUID();
    IndividualType type = IndividualType.CLIENT;
    IndividualEntity entity = individualEntityFactory.createDefault();
    Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
    when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

    PaginatedResult<Individual> result = serviceUnderTest.getIndividuals(1, 10, appId, type);

    verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    assertThat(result.page()).hasSize(1);
    assertThat(result.page().getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
  }

  private void assertIndividualListsEqual(List<Individual> actualList, List<IndividualEntity> expectedList) {
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

  private void assertIndividualEqual(IndividualEntity expected, Individual actual) {
    assertThat(actual.getFirstName()).isEqualTo(expected.getFirstName());
    assertThat(actual.getLastName()).isEqualTo(expected.getLastName());
    assertThat(actual.getDateOfBirth()).isEqualTo(expected.getDateOfBirth());
    assertThat(actual.getType()).isEqualTo(expected.getType());
    assertThat(actual.getDetails()).isEqualTo(expected.getIndividualContent());
  }
}
