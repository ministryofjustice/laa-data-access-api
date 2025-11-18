package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/**
 * Defines the filtering of Application Summaries.
 *
 */
public class ApplicationSummarySpecification {

  /**
   * Filters Application Summaries based on different filters.
   *
   */
  public static Specification<ApplicationSummaryEntity> filterBy(
          ApplicationStatus status,
          String reference) {
      return isStatus(status).and(isApplicationReference(reference));
  }

  private static Specification<ApplicationSummaryEntity> isStatus(ApplicationStatus status) {
    if (status != null) {
      return (root, query, builder)
              -> builder.equal(root.get("status"), status);
    }

    return Specification.unrestricted();
  }

  private static Specification<ApplicationSummaryEntity> isApplicationReference(String reference) {
    if (reference != null && !reference.isBlank()) {
      return (root, query, builder)
              -> builder.like(builder.lower(root.get("applicationReference")),
              "%" + reference.toLowerCase() + "%");
    }

    return Specification.unrestricted();
  }

}
