package uk.gov.justice.laa.dstew.access.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;

public class ApplicationRepositoryTest extends BaseIntegrationTest {

  @Test
  public void givenSaveOfExpectedApplication_whenGetCalled_expectedAndActualAreEqual() {

    // given
    ApplicationEntity expected = applicationFactory.create();
    applicationRepository.save(expected);

    // when
    ApplicationEntity actual = applicationRepository.findById(expected.getId()).orElse(null);

    // then
    assertApplicationEqual(expected, actual);
  }

  private void assertApplicationEqual(ApplicationEntity expected, ApplicationEntity actual) {
    assertThat(expected)
        .usingRecursiveComparison()
        .ignoringFields("createdAt", "modifiedAt")
        .isEqualTo(actual);
    assertThat(expected.getCreatedAt()).isNotNull();
    assertThat(expected.getModifiedAt()).isNotNull();
  }
}
