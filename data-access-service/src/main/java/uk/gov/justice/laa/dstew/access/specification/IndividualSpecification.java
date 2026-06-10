package uk.gov.justice.laa.dstew.access.specification;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/** Defines the filtering of Individuals. */
public class IndividualSpecification {
  /** Filters by applicationId using a join to applications (ManyToMany). */
  public static Specification<IndividualEntity> filterApplicationId(UUID applicationId) {
    if (applicationId == null) {
      return Specification.unrestricted();
    }
    // Join to applications and filter by applicationId
    // Subquery: SELECT 1 FROM ApplicationEntity a JOIN a.individuals i WHERE a.id = :applicationId
    // AND i.id = individual.id
    return (root, query, builder) -> {
      Subquery<UUID> subquery = query.subquery(UUID.class);
      Root<ApplicationEntity> appRoot = subquery.from(ApplicationEntity.class);
      subquery
          .select(appRoot.get("id"))
          .where(
              builder.and(
                  builder.equal(appRoot.get("id"), applicationId),
                  builder.isMember(
                      root, appRoot.<Collection<IndividualEntity>>get("individuals"))));
      return builder.exists(subquery);
    };
  }

  /** Filters by individualType. */
  public static Specification<IndividualEntity> filterIndividualType(
      IndividualType individualType) {
    if (individualType == null) {
      return Specification.unrestricted();
    }
    return (root, query, builder) -> builder.equal(root.get("type"), individualType);
  }
}
