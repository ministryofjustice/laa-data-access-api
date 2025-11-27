package uk.gov.justice.laa.dstew.access.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a combination of applications returned and paging data.
 */
@Getter
@Setter
public class ApplicationSummaryPage {
  private List<ApplicationSummary> applications;
  private long totalItems;
}
