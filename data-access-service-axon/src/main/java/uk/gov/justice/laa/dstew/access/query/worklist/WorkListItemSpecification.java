package uk.gov.justice.laa.dstew.access.query.worklist;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Database predicates for {@link FindWorkListItemsQuery}. */
public final class WorkListItemSpecification {

  private WorkListItemSpecification() {}

  /** Applies the selected work-item type and requested work-queue views. */
  public static Specification<WorkListItemReadModel> from(FindWorkListItemsQuery query) {
    return (root, criteriaQuery, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (query.itemType() != null) {
        predicates.add(criteriaBuilder.equal(root.get("itemType"), query.itemType()));
      }
      if (query.assignedToMe() && query.unassigned()) {
        predicates.add(
            criteriaBuilder.or(
                criteriaBuilder.isNull(root.get("assigneeId")),
                criteriaBuilder.equal(root.get("assigneeId"), query.authenticatedUserId())));
      } else if (query.assignedToMe()) {
        predicates.add(criteriaBuilder.equal(root.get("assigneeId"), query.authenticatedUserId()));
      } else {
        predicates.add(criteriaBuilder.isNull(root.get("assigneeId")));
      }
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }
}
