package uk.gov.justice.laa.dstew.access.query.application.search;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSearchView;

public final class ApplicationSearchSpecification {

  private ApplicationSearchSpecification() {}

  public static Specification<ApplicationSearchView> withFilters(
      String status,
      String laaReference,
      String caseworkerId,
      String matterType,
      Boolean isAutoGranted, String clientFirstName, String clientLastName) {

    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

      if (status != null && !status.isBlank()) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (laaReference != null && !laaReference.isBlank()) {
        predicates.add(cb.equal(root.get("laaReference"), laaReference));
      }
      if (caseworkerId != null && !caseworkerId.isBlank()) {
        predicates.add(cb.equal(root.get("caseworkerId"), caseworkerId));
      }
      if (matterType != null && !matterType.isBlank()) {
        predicates.add(cb.equal(root.get("matterType"), matterType));
      }
      if (isAutoGranted != null) {
        predicates.add(cb.equal(root.get("isAutoGranted"), isAutoGranted));
      }
      if (clientFirstName != null && !clientFirstName.isBlank()) {
        predicates.add(cb.like(
            cb.lower(root.get("clientFirstName"))
            , cb.literal("%" + clientFirstName.toLowerCase() + "%")));
      }
      if (clientLastName != null && !clientLastName.isBlank()) {
        predicates.add(cb.like(
            cb.lower(root.get("clientLastName"))
            , cb.literal("%" + clientLastName.toLowerCase() + "%")));
      }

      return predicates.isEmpty()
          ? cb.conjunction()
          : cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }
}
