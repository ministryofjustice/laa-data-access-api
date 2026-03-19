package uk.gov.justice.laa.dstew.access.specification;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/**
 * Defines the filtering of Individuals.
 *
 */
public class IndividualSpecification {
  /**
   * Filters by applicationId using a subquery (since the bidirectional relationship was removed).
   */
  public static Specification<IndividualEntity> filterApplicationId(UUID applicationId) {
    if (applicationId == null) {
      return Specification.unrestricted();
    }
    // Use a subquery to find individuals linked to the application through the join table
    return (root, query, builder) -> {
      var appSubquery = query.subquery(UUID.class);
      var appRoot = appSubquery.from(ApplicationEntity.class);
      var joinedIndividuals = appRoot.join("individuals");
      appSubquery.select(appRoot.get("id")).where(
          builder.and(
              builder.equal(appRoot.get("id"), applicationId),
              builder.equal(joinedIndividuals.get("id"), root.get("id"))
          )
      );
      return builder.exists(appSubquery);
    };
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
