package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;

/** Maps the generated HTTP request model to the Axon submit-application-draft command. */
@Component
public class SubmitApplicationDraftCommandMapper {

  /** Creates a submit command for the given Application ID. */
  public SubmitApplicationDraftCommand toSubmitCommand(UUID applicationId) {
    return new SubmitApplicationDraftCommand(applicationId, Instant.now());
  }
}
