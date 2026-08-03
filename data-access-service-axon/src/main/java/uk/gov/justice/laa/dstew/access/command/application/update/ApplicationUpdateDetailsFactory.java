package uk.gov.justice.laa.dstew.access.command.application.update;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Reconstructs a complete immutable data payload from an Application PATCH. */
@Component
public class ApplicationUpdateDetailsFactory {

  private final ApplicationContentParser applicationContentParser;

  public ApplicationUpdateDetailsFactory(ApplicationContentParser applicationContentParser) {
    this.applicationContentParser = applicationContentParser;
  }

  /** Parses replacement content while preserving stable internal proceeding identifiers. */
  public ApplicationDataPayload prepare(
      UpdateApplicationCommand command, ApplicationDataPayload current, boolean resetAssessment) {
    var parsed = applicationContentParser.parse(command.applicationContent());
    if (!command.applicationId().equals(parsed.applyApplicationId())) {
      throw new ValidationException(
          java.util.List.of("applicationContent.id: must match the Application path id"));
    }
    Map<UUID, ApplicationProceeding> currentProceedings =
        current.proceedings().stream()
            .collect(
                Collectors.toMap(ApplicationProceeding::applyProceedingId, Function.identity()));
    var proceedings =
        parsed.proceedings().stream()
            .map(proceeding -> toApplicationProceeding(proceeding, currentProceedings))
            .toList();
    return current.withApplicationUpdate(
        parsed.applicationContent(),
        parsed.submittedAt(),
        parsed.officeCode(),
        parsed.usedDelegatedFunctions(),
        parsed.categoryOfLaw(),
        parsed.matterType(),
        proceedings,
        command.serialisedRequest(),
        resetAssessment);
  }

  private ApplicationProceeding toApplicationProceeding(
      Proceeding proceeding, Map<UUID, ApplicationProceeding> currentProceedings) {
    ApplicationProceeding existing = currentProceedings.get(proceeding.getId());
    return new ApplicationProceeding(
        existing == null ? UUID.randomUUID() : existing.proceedingId(),
        proceeding.getId(),
        proceeding.getDescription(),
        Boolean.TRUE.equals(proceeding.getLeadProceeding()),
        proceeding);
  }
}
