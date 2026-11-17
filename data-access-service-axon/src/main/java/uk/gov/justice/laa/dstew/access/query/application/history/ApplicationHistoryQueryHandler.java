package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.lifecycle.LifecycleStreamReader;
import uk.gov.justice.laa.dstew.access.query.lifecycle.MissingStoredEventException;

/** Answers history queries from an application's lifecycle stream; it stores nothing itself. */
@Component
@RequiredArgsConstructor
public class ApplicationHistoryQueryHandler {

  private final LifecycleStreamReader lifecycleStream;
  private final ApplicationHistoryMapper mapper;
  private final ApplicationHistoryAssembler applicationHistoryAssembler;
  private final PriorAuthorityHistoryAssembler priorAuthorityHistoryAssembler;

  /**
   * Returns the application's history entries of the requested types, in the order they occurred,
   * and its Prior Authority history.
   *
   * @throws ApplicationHistoryIntegrityException if the stream points to a missing event, or the
   *     events are inconsistent
   */
  @QueryHandler
  public ApplicationHistoryResult handle(FindApplicationHistoryQuery query) {
    List<EventMessage> events;
    try {
      events = lifecycleStream.read(query.applicationId());
    } catch (MissingStoredEventException exception) {
      throw new ApplicationHistoryIntegrityException(
          query.applicationId(), null, exception.getMessage());
    }
    var rows = mapper.map(query.applicationId(), events);
    List<ApplicationHistoryRow> requested =
        rows.application().stream()
            .filter(row -> query.eventTypes().contains(row.getEventType()))
            .toList();
    return new ApplicationHistoryResult(
        applicationHistoryAssembler.assemble(requested),
        priorAuthorityHistoryAssembler.assemble(rows.priorAuthority()));
  }
}
