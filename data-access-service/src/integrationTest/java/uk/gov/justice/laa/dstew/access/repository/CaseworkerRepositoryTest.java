package uk.gov.justice.laa.dstew.access.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;

public class CaseworkerRepositoryTest extends BaseIntegrationTest {

  @Test
  public void givenSaveOfExpectedCaseworker_whenGetCalled_expectedAndActualAreEqual() {

    // given
    CaseworkerEntity expected = caseworkerFactory.create();
    caseworkerRepository.save(expected);

    // when
    CaseworkerEntity actual = caseworkerRepository.findById(expected.getId()).orElse(null);

    // then
    assertCaseworkerEqual(expected, actual);
  }

  private void assertCaseworkerEqual(CaseworkerEntity expected, CaseworkerEntity actual) {
    assertThat(expected)
        .usingRecursiveComparison()
        .ignoringFields("createdAt", "modifiedAt")
        .isEqualTo(actual);
    assertThat(expected.getCreatedAt()).isNotNull();
    assertThat(expected.getModifiedAt()).isNotNull();
  }
}
