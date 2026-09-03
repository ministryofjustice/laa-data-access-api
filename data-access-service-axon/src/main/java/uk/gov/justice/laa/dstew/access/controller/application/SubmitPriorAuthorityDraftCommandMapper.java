package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftCommand;

/** Maps the generated HTTP request model to the Axon submit-prior-authority-draft command. */
@Component
public class SubmitPriorAuthorityDraftCommandMapper {

  /** Creates a submit command for the given submission ID. */
  public SubmitPriorAuthorityDraftCommand toSubmitCommand(UUID submissionId) {
    return new SubmitPriorAuthorityDraftCommand(submissionId, Instant.now());
  }
}
