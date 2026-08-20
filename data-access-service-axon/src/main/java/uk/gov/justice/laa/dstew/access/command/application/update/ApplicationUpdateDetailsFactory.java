package uk.gov.justice.laa.dstew.access.command.application.update;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;

/** Reconstructs a complete immutable data payload from an Application PATCH. */
@Component
public class ApplicationUpdateDetailsFactory {

  private final ApplicationContentParser applicationContentParser;

  public ApplicationUpdateDetailsFactory(ApplicationContentParser applicationContentParser) {
    this.applicationContentParser = applicationContentParser;
  }

  /** Parses replacement content and builds an updated data payload. */
  public ApplicationDataPayload prepare(
      UpdateApplicationCommand command, ApplicationDataPayload current, boolean resetAssessment) {
    var parsed = applicationContentParser.parse(command.applicationContent());
    return current.withApplicationUpdate(
        parsed.client(),
        parsed.provider(),
        parsed.opponents(),
        parsed.submittedAt(),
        parsed.usedDelegatedFunctions(),
        parsed.categoryOfLaw(),
        parsed.matterType(),
        parsed.proceedings(),
        command.serialisedRequest(),
        resetAssessment);
  }
}
