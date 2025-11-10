package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;

/**
 * Defines the filtering of Application Summaries.
 *
 */
public class ApplicationSummarySpecification {

  /**
   * Filters Application Summaries based on status code.
   *
  */
  public static Specification<ApplicationSummaryEntity> isStatus(String status) {
    return (root, query, builder) -> {
      Path<Object> statusCodeEntityPath = root.get("statusCodeLookupEntity");
      return builder.equal(statusCodeEntityPath.get("code"), status);
    };
  }

  /**
   * Filters Application Summaries based on reference.
   *
   */
  public static Specification<ApplicationSummaryEntity> isApplicationReference(String reference) {
    return (root, query, builder) -> {
      return builder.equal(root.get("applicationReference"), reference);
    };
  }
}
