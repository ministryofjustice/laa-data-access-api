package uk.gov.justice.laa.dstew.access.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationHelperTest {

  @Test
  void givenNullPage_whenValidatePage_thenReturnDefaultPage() {
    // when
    int result = PaginationHelper.validatePage(null);

    // then
    assertThat(result).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 10, 100, 1000})
  void givenValidPage_whenValidatePage_thenReturnSamePage(int page) {
    // when
    int result = PaginationHelper.validatePage(page);

    // then
    assertThat(result).isEqualTo(page);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -10, -100})
  void givenInvalidPage_whenValidatePage_thenThrowIllegalArgumentException(int page) {
    // when/then
    assertThatThrownBy(() -> PaginationHelper.validatePage(page))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("page must be greater than or equal to 1");
  }

  @Test
  void givenNullPageSize_whenValidatePageSize_thenReturnDefaultPageSize() {
    // when
    int result = PaginationHelper.validatePageSize(null);

    // then
    assertThat(result).isEqualTo(20);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 10, 50, 100})
  void givenValidPageSize_whenValidatePageSize_thenReturnSamePageSize(int pageSize) {
    // when
    int result = PaginationHelper.validatePageSize(pageSize);

    // then
    assertThat(result).isEqualTo(pageSize);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -10, -100})
  void givenPageSizeBelowMinimum_whenValidatePageSize_thenThrowIllegalArgumentException(int pageSize) {
    // when/then
    assertThatThrownBy(() -> PaginationHelper.validatePageSize(pageSize))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize must be greater than or equal to 1");
  }

  @ParameterizedTest
  @ValueSource(ints = {101, 200, 500, 1000})
  void givenPageSizeAboveMaximum_whenValidatePageSize_thenThrowIllegalArgumentException(int pageSize) {
    // when/then
    assertThatThrownBy(() -> PaginationHelper.validatePageSize(pageSize))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize cannot be more than 100");
  }

  @Test
  void givenOneBasedPageOne_whenCreatePageable_thenReturnZeroBasedPageZero() {
    // when
    Pageable result = PaginationHelper.createPageable(1, 10);

    // then
    assertThat(result.getPageNumber()).isEqualTo(0);
    assertThat(result.getPageSize()).isEqualTo(10);
    assertThat(result.getSort().isUnsorted()).isTrue();
  }

  @Test
  void givenOneBasedPageThree_whenCreatePageable_thenReturnZeroBasedPageTwo() {
    // when
    Pageable result = PaginationHelper.createPageable(3, 25);

    // then
    assertThat(result.getPageNumber()).isEqualTo(2);
    assertThat(result.getPageSize()).isEqualTo(25);
    assertThat(result.getSort().isUnsorted()).isTrue();
  }

  @Test
  void givenSortParameter_whenCreatePageable_thenReturnPageableWithSort() {
    // given
    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

    // when
    Pageable result = PaginationHelper.createPageable(1, 10, sort);

    // then
    assertThat(result.getPageNumber()).isEqualTo(0);
    assertThat(result.getPageSize()).isEqualTo(10);
    assertThat(result.getSort()).isEqualTo(sort);
    assertThat(result.getSort().isSorted()).isTrue();
    assertThat(result.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void givenPageResult_whenWrapResult_thenReturnPaginatedResultWithCorrectValues() {
    // given
    Page<String> pageResult = new PageImpl<>(List.of("item1", "item2"));

    // when
    PaginationHelper.PaginatedResult<String> result = PaginationHelper.wrapResult(2, 15, pageResult);

    // then
    assertThat(result.page()).isEqualTo(pageResult);
    assertThat(result.page().getContent()).containsExactly("item1", "item2");
    assertThat(result.requestedPage()).isEqualTo(2);
    assertThat(result.requestedPageSize()).isEqualTo(15);
  }

  @Test
  void givenNullInputs_whenWrapResult_thenReturnPaginatedResultWithDefaults() {
    // given
    Page<String> pageResult = new PageImpl<>(List.of("item1"));

    // when
    PaginationHelper.PaginatedResult<String> result = PaginationHelper.wrapResult(null, null, pageResult);

    // then
    assertThat(result.page()).isEqualTo(pageResult);
    assertThat(result.page().getContent()).containsExactly("item1");
    assertThat(result.requestedPage()).isEqualTo(1);
    assertThat(result.requestedPageSize()).isEqualTo(20);
  }
}
