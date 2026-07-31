package uk.gov.justice.laa.dstew.access.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PaginationHelperTest {

  @Test
  void givenAbsentValues_whenValidated_thenReturnsDefaults() {
    assertThat(PaginationHelper.validatePage(null)).isEqualTo(1);
    assertThat(PaginationHelper.validatePageSize(null)).isEqualTo(20);
  }

  @Test
  void givenValidValues_whenValidated_thenReturnsValues() {
    assertThat(PaginationHelper.validatePage(3)).isEqualTo(3);
    assertThat(PaginationHelper.validatePageSize(100)).isEqualTo(100);
  }

  @Test
  void givenPageBelowMinimum_whenValidated_thenRejectsValue() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PaginationHelper.validatePage(0))
        .withMessage("page must be greater than or equal to 1");
  }

  @Test
  void givenPageSizeOutsideRange_whenValidated_thenRejectsValue() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PaginationHelper.validatePageSize(0))
        .withMessage("pageSize must be greater than or equal to 1");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PaginationHelper.validatePageSize(101))
        .withMessage("pageSize cannot be more than 100");
  }
}
