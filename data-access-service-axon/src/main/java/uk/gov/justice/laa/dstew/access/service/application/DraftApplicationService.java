package uk.gov.justice.laa.dstew.access.service.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.PutDraftApplicationCommand;
import uk.gov.justice.laa.dstew.access.query.draft.DraftRecord;
import uk.gov.justice.laa.dstew.access.query.draft.DraftRepository;

/**
 * Application-layer entry point for the application draft lifecycle.
 *
 * <p>Keeps personal data out of the event stream: it persists the raw draft body to the deletable
 * {@code drafts} table keyed by the application id, then dispatches a PII-free pointer command. The
 * aggregate therefore never sees the draft content.
 *
 * <p>The command is dispatched first so the aggregate can guard the transition (a submitted
 * application can no longer be edited as a draft) before the mutable body is overwritten. The draft
 * is created or overwritten in place — last write wins.
 */
@Service
public class DraftApplicationService {

  private final DraftRepository repository;
  private final CommandGateway commandGateway;
  private final Clock clock;

  /** Wires the draft store, command gateway and clock collaborators. */
  public DraftApplicationService(
      DraftRepository repository, CommandGateway commandGateway, Clock clock) {
    this.repository = repository;
    this.commandGateway = commandGateway;
    this.clock = clock;
  }

  /**
   * Creates or overwrites the draft for the given application, persisting the raw body.
   *
   * <p>The application id is supplied by the caller. This is an interim accommodation while Civil
   * Apply are not yet ready to consume a service-minted id; in the target flow the datastore mints
   * it at draft creation and this method would generate the id itself.
   *
   * @param applicationId the client-supplied application id
   * @param content the raw, unvalidated draft body (personal data kept out of the event stream)
   */
  public void putDraft(UUID applicationId, Map<String, Object> content) {
    commandGateway.sendAndWait(new PutDraftApplicationCommand(applicationId));
    Instant now = Instant.now(clock);
    DraftRecord record =
        repository
            .findById(applicationId)
            .orElseGet(
                () ->
                    DraftRecord.builder().applyApplicationId(applicationId).createdAt(now).build());
    record.setContent(content);
    record.setUpdatedAt(now);
    repository.save(record);
  }
}
