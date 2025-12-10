package uk.gov.justice.laa.dstew.access.specification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

/**
 * Defines the filtering of Domain Events.
 *
 */
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
  * the colleciton is empty.
  *
  */
  public static Specification<DomainEventEntity> filterMultipleEventType(List<DomainEventType> eventTypes) {
    if (eventTypes == null || eventTypes.isEmpty()) {
      return Specification.unrestricted();
    }
    return eventTypes.stream()
                     .map(DomainEventSpecification::filterEventType)
                     .reduce(Specification.unrestricted(), (a, b) -> a.or(b));
  }

  private static Specification<DomainEventEntity> filterEventType(DomainEventType eventType) {
    return (root, query, builder)
        -> builder.equal(root.get("type"), eventType);
  }
}
