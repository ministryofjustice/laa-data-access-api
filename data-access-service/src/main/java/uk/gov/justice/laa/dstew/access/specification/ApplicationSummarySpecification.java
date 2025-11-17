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
          ApplicationStatus status) {
    return (Root<ApplicationSummaryEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
