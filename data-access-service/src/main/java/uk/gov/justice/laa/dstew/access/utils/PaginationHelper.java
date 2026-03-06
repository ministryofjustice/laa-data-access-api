package uk.gov.justice.laa.dstew.access.utils;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Helper class for pagination validation.
 */
public final class PaginationHelper {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final int MIN_PAGE = 1;
  private static final int MIN_PAGE_SIZE = 1;

  private PaginationHelper() {
    // Utility class, prevent instantiation
  }

  /**
   * Wrapper for paginated results that preserves the one-based page number.
   *
   * @param <T> the type of content in the page
   * @param page the Spring Data Page result
   * @param requestedPage the validated one-based page number
   * @param requestedPageSize the validated page size
   */
  public record PaginatedResult<T>(Page<T> page, int requestedPage, int requestedPageSize) {}

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
   * Wraps a Page result with the validated one-based pagination parameters.
   *
   * @param <T> the type of content in the page
   * @param page the one-based page number (from API)
   * @param pageSize the page size (from API)
   * @param result the Page result from the repository
   * @return a PaginatedResult containing the page and validated parameters
   */
  public static <T> PaginatedResult<T> wrapResult(Integer page, Integer pageSize, Page<T> result) {
    return new PaginatedResult<>(result, validatePage(page), validatePageSize(pageSize));
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
      throw new IllegalArgumentException("pageSize must be greater than or equal to " + MIN_PAGE_SIZE);
    }
    if (pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("pageSize cannot be more than " + MAX_PAGE_SIZE);
    }
    return pageSize;
  }
}
