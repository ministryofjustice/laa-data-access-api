package uk.gov.justice.laa.dstew.access.query;

/** Shared validation and defaulting for one-based API pagination parameters. */
public final class PaginationHelper {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private PaginationHelper() {}

  /** Returns the supplied one-based page, or the default when it is absent. */
  public static int validatePage(Integer page) {
    if (page == null) {
      return DEFAULT_PAGE;
    }
    if (page < 1) {
      throw new IllegalArgumentException("page must be greater than or equal to 1");
    }
    return page;
  }

  /** Returns the supplied page size, or the default when it is absent. */
  public static int validatePageSize(Integer pageSize) {
    if (pageSize == null) {
      return DEFAULT_PAGE_SIZE;
    }
    if (pageSize < 1) {
      throw new IllegalArgumentException("pageSize must be greater than or equal to 1");
    }
    if (pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("pageSize cannot be more than " + MAX_PAGE_SIZE);
    }
    return pageSize;
  }
}
