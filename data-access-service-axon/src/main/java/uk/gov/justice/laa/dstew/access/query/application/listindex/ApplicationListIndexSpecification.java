package uk.gov.justice.laa.dstew.access.query.application.listindex;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;

/**
 * Builds a JPA {@link Specification} from a {@link FindAllApplicationsQuery}.
 *
 * <p>All non-null filter fields produce a database predicate so that filtering and counting happen
 * entirely in PostgreSQL before any rows are returned to the application. Client-name predicates
 * use {@code lower()} to match the functional indexes created by the V15 migration.
 */
public final class ApplicationListIndexSpecification {

  private ApplicationListIndexSpecification() {}

  /** Returns a {@link Specification} that applies every non-null filter in the query. */
  public static Specification<ApplicationListIndexReadModel> from(FindAllApplicationsQuery query) {
    return (root, criteriaQuery, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (query.status() != null) {
        predicates.add(cb.equal(root.get("status"), query.status()));
      }
      if (query.laaReference() != null) {
        predicates.add(cb.equal(root.get("laaReference"), query.laaReference()));
      }
      if (query.matterType() != null) {
        predicates.add(cb.equal(root.get("matterType"), query.matterType()));
      }
      if (query.clientFirstName() != null) {
        predicates.add(likeIgnoreCase(cb, root, "clientFirstName", query.clientFirstName()));
      }
      if (query.clientLastName() != null) {
        predicates.add(likeIgnoreCase(cb, root, "clientLastName", query.clientLastName()));
      }
      if (query.clientDateOfBirth() != null) {
        predicates.add(cb.equal(root.get("clientDateOfBirth"), query.clientDateOfBirth()));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Builds a case-insensitive prefix predicate matching the {@code lower(column)} functional
   * indexes created in the V15 migration.
   */
  private static Predicate likeIgnoreCase(
      CriteriaBuilder cb, Root<ApplicationListIndexReadModel> root, String field, String value) {
    return cb.like(cb.lower(root.get(field)), value.toLowerCase() + "%");
  }
}
