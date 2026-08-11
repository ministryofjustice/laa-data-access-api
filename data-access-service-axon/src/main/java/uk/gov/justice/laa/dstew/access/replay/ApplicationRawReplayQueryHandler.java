package uk.gov.justice.laa.dstew.access.replay;

import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdRebuildQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdRebuildQueryNative;

/**
 * Handles {@link FindApplicationByIdRebuildQuery} by delegating to {@link
 * ApplicationRawReplayService}, which reconstructs the Application's current-state read model
 * directly from the raw Axon event store, bypassing the running projection entirely.
 */
@Component
public class ApplicationRawReplayQueryHandler {

  private final ApplicationRawReplayService rawReplayService;
  private final ApplicationEventStoreReplayService eventStoreReplayService;

  /** Constructs the handler with its raw-replay service. */
  public ApplicationRawReplayQueryHandler(
      ApplicationRawReplayService rawReplayService,
      ApplicationEventStoreReplayService eventStoreReplayService) {
    this.rawReplayService = rawReplayService;
    this.eventStoreReplayService = eventStoreReplayService;
  }

  /** Replays the raw event stream for the given Application and returns the rebuilt state. */
  @QueryHandler
  public @Nullable ApplicationReadModel handle(FindApplicationByIdRebuildQuery query) {
    return rawReplayService.replay(query.applicationId()).orElse(null);
  }

  @QueryHandler
  public @Nullable ApplicationReadModel handle(FindApplicationByIdRebuildQueryNative query) {
    return eventStoreReplayService.replay(query.applicationId()).orElse(null);
  }
}
