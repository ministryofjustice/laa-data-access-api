package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.eventcontent.RegisteredContentReaders;
import uk.gov.justice.laa.dstew.access.eventcontent.StoredEvents;
import uk.gov.justice.laa.dstew.access.query.genesis.GenesisStreamEntry;
import uk.gov.justice.laa.dstew.access.query.genesis.GenesisStreamProjection;
import uk.gov.justice.laa.dstew.access.query.genesis.GenesisStreamRepository;

/** Answers history queries from an application's genesis stream; it stores nothing itself. */
@Component
@RequiredArgsConstructor
public class ApplicationHistoryQueryHandler {

  private final GenesisStreamRepository genesisStream;
  private final StoredEvents storedEvents;

  /**
   * Returns the application's history in the order its events happened.
   *
   * @throws ApplicationHistoryIntegrityException if a pointer names an event that is not stored, or
   *     the events are inconsistent
   */
  @QueryHandler
  public ApplicationHistoryResult handle(FindApplicationHistoryQuery query) {
    List<GenesisStreamEntry> pointers =
        genesisStream.findAllByGenesisIdOrderByOccurredAtAscIdAsc(query.applicationId()).stream()
            .filter(pointer -> GenesisStreamProjection.LOCAL_SOURCE.equals(pointer.getSource()))
            .toList();
    Map<String, EventMessage> events =
        storedEvents.load(
            RegisteredContentReaders.APPLICATION_HISTORY,
            pointers.stream().map(GenesisStreamEntry::getEventIdentifier).toList());
    List<EventMessage> ordered =
        pointers.stream()
            .map(
                pointer -> {
                  EventMessage event = events.get(pointer.getEventIdentifier());
                  if (event == null) {
                    throw new ApplicationHistoryIntegrityException(
                        query.applicationId(),
                        null,
                        "no stored event " + pointer.getEventIdentifier());
                  }
                  return event;
                })
            .toList();
    return ApplicationHistoryMapper.map(query.applicationId(), ordered, query.eventTypes());
  }
}
