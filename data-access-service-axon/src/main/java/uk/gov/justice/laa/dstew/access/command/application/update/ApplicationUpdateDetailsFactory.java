package uk.gov.justice.laa.dstew.access.command.application.update;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;

/** Parses the replacement content of an Application PATCH. */
@Component
public class ApplicationUpdateDetailsFactory {

  private final ApplicationContentParser applicationContentParser;

  public ApplicationUpdateDetailsFactory(ApplicationContentParser applicationContentParser) {
    this.applicationContentParser = applicationContentParser;
  }

  /** Parses the replacement content of an Application PATCH. */
  public ParsedAppContentDetails parse(UpdateApplicationCommand command) {
    return applicationContentParser.parse(command.applicationContent());
  }
}
