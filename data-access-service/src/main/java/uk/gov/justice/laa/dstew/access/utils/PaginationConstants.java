package uk.gov.justice.laa.dstew.access.utils;

/**
 * Constants for pagination validation across all controllers.
 */
public final class PaginationConstants {

  /**
   * Default page number when not specified or invalid.
   */
  public static final int DEFAULT_PAGE = 1;

  /**
   * Default page size when not specified.
   */
  public static final int DEFAULT_PAGE_SIZE = 20;

  /**
   * Maximum allowed page size.
   */
  public static final int MAX_PAGE_SIZE = 100;

  /**
   * Minimum valid page number.
   */
  public static final int MIN_PAGE = 1;

  /**
   * Minimum valid page size.
   */
  public static final int MIN_PAGE_SIZE = 1;

  private PaginationConstants() {
    // Utility class, prevent instantiation
  }

  /**
   * Validates and normalizes the page parameter.
   *
   * @param page the page number to validate
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
