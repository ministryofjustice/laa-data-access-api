package uk.gov.justice.laa.dstew.access.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationConstantsTest {

  @Test
  void givenNullPage_whenValidatePage_thenReturnDefaultPage() {
    // when
    int result = PaginationConstants.validatePage(null);

    // then
    assertThat(result).isEqualTo(PaginationConstants.DEFAULT_PAGE);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 10, 100, 1000})
  void givenValidPage_whenValidatePage_thenReturnSamePage(int page) {
    // when
    int result = PaginationConstants.validatePage(page);

    // then
    assertThat(result).isEqualTo(page);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -10, -100})
  void givenInvalidPage_whenValidatePage_thenThrowIllegalArgumentException(int page) {
    // when/then
    assertThatThrownBy(() -> PaginationConstants.validatePage(page))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("page must be greater than or equal to " + PaginationConstants.MIN_PAGE);
  }

  @Test
  void givenNullPageSize_whenValidatePageSize_thenReturnDefaultPageSize() {
    // when
    int result = PaginationConstants.validatePageSize(null);

    // then
    assertThat(result).isEqualTo(PaginationConstants.DEFAULT_PAGE_SIZE);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 10, 50, 100})
  void givenValidPageSize_whenValidatePageSize_thenReturnSamePageSize(int pageSize) {
    // when
    int result = PaginationConstants.validatePageSize(pageSize);

    // then
    assertThat(result).isEqualTo(pageSize);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -10, -100})
  void givenPageSizeBelowMinimum_whenValidatePageSize_thenThrowIllegalArgumentException(int pageSize) {
    // when/then
    assertThatThrownBy(() -> PaginationConstants.validatePageSize(pageSize))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize must be greater than or equal to " + PaginationConstants.MIN_PAGE_SIZE);
  }

  @ParameterizedTest
  @ValueSource(ints = {101, 200, 500, 1000})
  void givenPageSizeAboveMaximum_whenValidatePageSize_thenThrowIllegalArgumentException(int pageSize) {
    // when/then
    assertThatThrownBy(() -> PaginationConstants.validatePageSize(pageSize))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize cannot be more than " + PaginationConstants.MAX_PAGE_SIZE);
  }
}
