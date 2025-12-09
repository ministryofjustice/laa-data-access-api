package uk.gov.justice.laa.dstew.access.specification;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;

/**
 * Defines the filtering of Domain Events.
 *
 */
public class DomainEventSpecification {
  public static Specification<DomainEventEntity> filterApplicationId(UUID appId) {
    return (root, query, builder)
        -> builder.equal(root.get("applicationId"), appId);
  }
}
