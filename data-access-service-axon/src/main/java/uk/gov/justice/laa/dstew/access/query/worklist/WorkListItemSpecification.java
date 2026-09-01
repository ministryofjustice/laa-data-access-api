package uk.gov.justice.laa.dstew.access.query.worklist;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Database predicates for {@link FindWorkListItemsQuery}. */
public final class WorkListItemSpecification {

  private WorkListItemSpecification() {}

  /** Applies the selected work-item type and exactly one queue view. */
  public static Specification<WorkListItemReadModel> from(FindWorkListItemsQuery query) {
    return (root, criteriaQuery, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (query.itemType() != null) {
        predicates.add(criteriaBuilder.equal(root.get("id").get("itemType"), query.itemType()));
      }
      if (query.assignedTo() != null) {
        predicates.add(criteriaBuilder.equal(root.get("assigneeId"), query.assignedTo()));
      } else if (Boolean.TRUE.equals(query.unassigned())) {
        predicates.add(criteriaBuilder.isNull(root.get("assigneeId")));
      }
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }
}
