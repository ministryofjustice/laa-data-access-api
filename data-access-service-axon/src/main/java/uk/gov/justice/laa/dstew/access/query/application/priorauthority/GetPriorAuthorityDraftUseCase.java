package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.PriorAuthorityResult;

/** Retrieves the current draft content for an in-progress Prior Authority submission. */
@Service
public class GetPriorAuthorityDraftUseCase {

  private final PriorAuthorityDraftStore draftStore;

  public GetPriorAuthorityDraftUseCase(PriorAuthorityDraftStore draftStore) {
    this.draftStore = draftStore;
  }

  /** Retrieves the draft identified by its submission ID. */
  public PriorAuthorityResult getPriorAuthorityDraft(UUID priorAuthorityId) {
    return draftStore
        .find(priorAuthorityId)
        .map(PriorAuthorityResult::fromDraft)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No prior authority draft found with ID: " + priorAuthorityId));
  }
}
