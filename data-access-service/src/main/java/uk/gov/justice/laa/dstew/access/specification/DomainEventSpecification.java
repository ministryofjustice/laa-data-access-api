package uk.gov.justice.laa.dstew.access.specification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

/**
 * Defines the filtering of Domain Events.
 *
 */
@ExcludeFromGeneratedCodeCoverage
public class DomainEventSpecification {
  /**
  * Filters to a specific application.
  *
  */
  public static Specification<DomainEventEntity> filterApplicationId(UUID appId) {
    return (root, query, builder)
        -> builder.equal(root.get("applicationId"), appId);
  }

  /**
  * Filters to one of the supplied domain types, does no filtering if 
  * the collection is empty.
  *
  */
  public static Specification<DomainEventEntity> filterEventTypes(List<DomainEventType> eventTypes) {
    return (eventTypes == null || eventTypes.isEmpty())  
            ? Specification.unrestricted()  
            : (root, query, builder) -> root.get("type").in(eventTypes);  
  }
}
