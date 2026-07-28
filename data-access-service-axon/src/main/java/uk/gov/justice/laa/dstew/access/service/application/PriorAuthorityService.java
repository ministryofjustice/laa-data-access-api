package uk.gov.justice.laa.dstew.access.service.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.SubmitPriorAuthorityCommand;
import uk.gov.justice.laa.dstew.access.command.application.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.query.priorauthority.PriorAuthorityDraftRecord;
import uk.gov.justice.laa.dstew.access.query.priorauthority.PriorAuthorityDraftRepository;

/**
 * Application-layer entry point for the prior authority draft lifecycle.
 *
 * <p>Keeps personal data out of the event stream: it persists the draft body to the deletable
 * {@code prior_authority_drafts} table keyed by {@code priorAuthorityId}, then dispatches PII-free
 * pointer commands. The aggregate therefore never sees the draft content.
 *
 * <p>On create the body is written first, then the command dispatched; if the command is rejected
 * (for example, the application is not yet submitted) the orphaned body row is removed. On update
 * the command is dispatched first so the aggregate can guard the transition before the mutable body
 * is overwritten.
 */
@Service
public class PriorAuthorityService {

  private final PriorAuthorityDraftRepository repository;
  private final CommandGateway commandGateway;
  private final Clock clock;

  /** Wires the draft store, command gateway and clock collaborators. */
  public PriorAuthorityService(
      PriorAuthorityDraftRepository repository, CommandGateway commandGateway, Clock clock) {
    this.repository = repository;
    this.commandGateway = commandGateway;
    this.clock = clock;
  }

  /**
   * Persists the draft body and creates a prior authority draft against the application.
   *
   * @param applicationId the owning application
   * @param content the free-form draft body (personal data kept out of the event stream)
   * @return the minted prior authority identifier
   */
  public UUID createDraft(UUID applicationId, Map<String, Object> content) {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant now = Instant.now(clock);
    repository.save(
        PriorAuthorityDraftRecord.builder()
            .priorAuthorityId(priorAuthorityId)
            .applyApplicationId(applicationId)
            .content(content)
            .createdAt(now)
            .updatedAt(now)
            .build());
    try {
      commandGateway.sendAndWait(
          new CreatePriorAuthorityDraftCommand(applicationId, priorAuthorityId));
    } catch (RuntimeException e) {
      repository.deleteById(priorAuthorityId);
      throw e;
    }
    return priorAuthorityId;
  }

  /**
   * Guards the transition through the aggregate, then overwrites the mutable draft body in place.
   *
   * @param applicationId the owning application
   * @param priorAuthorityId the prior authority to update
   * @param content the latest draft body
   */
  public void updateDraft(UUID applicationId, UUID priorAuthorityId, Map<String, Object> content) {
    commandGateway.sendAndWait(
        new UpdatePriorAuthorityDraftCommand(applicationId, priorAuthorityId));
    Instant now = Instant.now(clock);
    PriorAuthorityDraftRecord record =
        repository
            .findById(priorAuthorityId)
            .orElseGet(
                () ->
                    PriorAuthorityDraftRecord.builder()
                        .priorAuthorityId(priorAuthorityId)
                        .applyApplicationId(applicationId)
                        .createdAt(now)
                        .build());
    record.setContent(content);
    record.setUpdatedAt(now);
    repository.save(record);
  }

  /**
   * Submits a prior authority draft, transitioning it from {@code DRAFT} to {@code SUBMITTED}.
   *
   * @param applicationId the owning application
   * @param priorAuthorityId the prior authority to submit
   */
  public void submit(UUID applicationId, UUID priorAuthorityId) {
    commandGateway.sendAndWait(new SubmitPriorAuthorityCommand(applicationId, priorAuthorityId));
  }
}
