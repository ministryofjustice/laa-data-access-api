package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.springframework.stereotype.Component;

/** Reads finite historical streams without hydrating sensitive content. */
@Component
public class AggregateEventHistoryReader {

  private final EventStore eventStore;

  public AggregateEventHistoryReader(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  /** Returns all matching events, closing the stream even when consumption fails. */
  public List<EventMessage> read(
      String aggregateType, UUID aggregateId, ProcessingContext context) {
    var criteria = EventCriteria.havingTags(Tag.of(aggregateType, aggregateId.toString()));
    var stream = eventStore.transaction(context).source(SourcingCondition.conditionFor(criteria));
    try {
      return stream
          .filter(
              entry -> aggregateType.equals(entry.getResource(LegacyResources.AGGREGATE_TYPE_KEY)))
          .collect(() -> new ArrayList<EventMessage>(), List::add)
          .join();
    } finally {
      stream.close();
    }
  }
}
