package uk.gov.justice.laa.dstew.access.specification;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/**
 * Defines the filtering of Individuals.
 *
 */
public class IndividualSpecification {
  /**
   * Filters by applicationId using a join to applications (ManyToMany).
   */
  public static Specification<IndividualEntity> filterApplicationId(UUID applicationId) {
    if (applicationId == null) {
      return Specification.unrestricted();
    }
    // Join to applications and filter by applicationId
    return (root, query, builder) ->
        builder.equal(
            root.join("applications").get("id"),
            applicationId
        );
  }

  /**
   * Filters by individualType.
   */
  public static Specification<IndividualEntity> filterIndividualType(IndividualType individualType) {
    if (individualType == null) {
      return Specification.unrestricted();
    }
    return (root, query, builder)
        -> builder.equal(root.get("type"), individualType);
  }
}
