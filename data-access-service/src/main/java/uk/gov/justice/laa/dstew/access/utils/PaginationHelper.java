package uk.gov.justice.laa.dstew.access.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Helper class for pagination validation. */
public final class PaginationHelper {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final int MIN_PAGE = 1;
  private static final int MIN_PAGE_SIZE = 1;

  @ExcludeFromGeneratedCodeCoverage
  private PaginationHelper() {
    // Utility class, prevent instantiation
  }

  /**
   * Validates pagination parameters and returns a zero-based Pageable.
   *
   * @param page the one-based page number
   * @param pageSize the page size
   * @return a validated Pageable for Spring Data queries
   */
  public static Pageable createPageable(Integer page, Integer pageSize) {
    int validatedPage = validatePage(page);
    int validatedPageSize = validatePageSize(pageSize);
    return PageRequest.of(validatedPage - 1, validatedPageSize);
  }

  /**
   * Validates pagination parameters and returns a zero-based Pageable with sorting.
   *
   * @param page the one-based page number
   * @param pageSize the page size
   * @param sort the sort specification
   * @return a validated Pageable for Spring Data queries
   */
  public static Pageable createPageable(Integer page, Integer pageSize, Sort sort) {
    int validatedPage = validatePage(page);
    int validatedPageSize = validatePageSize(pageSize);
    return PageRequest.of(validatedPage - 1, validatedPageSize, sort);
  }

  /**
   * Validates and normalizes the page parameter.
   *
   * @param page the page number to validate (one-based)
   * @return the validated page number
   * @throws IllegalArgumentException if page is less than MIN_PAGE
   */
  public static int validatePage(Integer page) {
    if (page == null) {
      return DEFAULT_PAGE;
    }
    if (page < MIN_PAGE) {
      throw new IllegalArgumentException("page must be greater than or equal to " + MIN_PAGE);
    }
    return page;
  }

  /**
   * Validates and normalizes the pageSize parameter.
   *
   * @param pageSize the page size to validate
   * @return the validated page size
   * @throws IllegalArgumentException if pageSize is invalid
   */
  public static int validatePageSize(Integer pageSize) {
    if (pageSize == null) {
      return DEFAULT_PAGE_SIZE;
    }
    if (pageSize < MIN_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "pageSize must be greater than or equal to " + MIN_PAGE_SIZE);
    }
    if (pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("pageSize cannot be more than " + MAX_PAGE_SIZE);
    }
    return pageSize;
  }
}
