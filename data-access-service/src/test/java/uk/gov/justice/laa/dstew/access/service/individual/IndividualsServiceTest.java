package uk.gov.justice.laa.dstew.access.service.individual;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    when(individualRepository.findAll(any(Pageable.class))).thenReturn(pageResult);

    // when
    List<Individual> actualIndividuals = serviceUnderTest.getIndividuals(1, 10).page().stream().toList();

    // then
    verify(individualRepository, times(1)).findAll(any(Pageable.class));
    assertIndividualListsEqual(actualIndividuals, expectedIndividuals);
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
