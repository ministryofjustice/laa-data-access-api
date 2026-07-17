package uk.gov.justice.laa.dstew.access.service.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionData;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionRecord;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionRepository;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionType;
import uk.gov.justice.laa.dstew.access.validation.DuplicateApplyApplicationIdException;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/**
 * Application-layer entry point for submitting an application.
 *
 * <p>Keeps personal data out of the event stream: it validates and parses the raw content, persists
 * the submitted body to the deletable {@code submissions} table under a minted {@code contentId},
 * then dispatches a PII-free {@link CreateApplicationCommand} carrying only that pointer and
 * non-PII structural metadata. The aggregate therefore never sees personal data. If the command is
 * rejected as a duplicate, the orphaned body row is removed.
 */
@Service
public class SubmitApplicationService {

  private final ApplicationContentParser parser;
  private final JsonSchemaValidator jsonSchemaValidator;
  private final SubmissionRepository submissionRepository;
  private final CommandGateway commandGateway;
  private final Clock clock;

  /** Wires the parsing, validation, payload store, command gateway and clock collaborators. */
  public SubmitApplicationService(
      ApplicationContentParser parser,
      JsonSchemaValidator jsonSchemaValidator,
      SubmissionRepository submissionRepository,
      CommandGateway commandGateway,
      Clock clock) {
    this.parser = parser;
    this.jsonSchemaValidator = jsonSchemaValidator;
    this.submissionRepository = submissionRepository;
    this.commandGateway = commandGateway;
    this.clock = clock;
  }

  /**
   * Validates, persists the body and submits the application, returning the Apply Application id.
   *
   * @param request the create request whose content holds the personal data
   * @param schemaVersion the JSON schema version to validate the content against
   * @return the Apply Application identifier of the submitted application
   */
  public UUID submit(ApplicationCreateRequest request, int schemaVersion) {
    jsonSchemaValidator.validate(
        request.getApplicationContent(), schemaName(request), schemaVersion);
    ParsedAppContentDetails parsed = parser.parse(request.getApplicationContent());

    UUID applyApplicationId = parsed.applyApplicationId();
    UUID contentId = UUID.randomUUID();

    submissionRepository.save(
        SubmissionRecord.builder()
            .contentId(contentId)
            .applyApplicationId(applyApplicationId)
            .submissionType(SubmissionType.CIVIL_APPLICATION)
            .data(
                new SubmissionData(
                    parsed.applicationContent(),
                    toIndividuals(request.getIndividuals()),
                    toProceedings(parsed.proceedings())))
            .createdAt(Instant.now(clock))
            .build());

    CreateApplicationCommand command =
        new CreateApplicationCommand(
            applyApplicationId,
            contentId,
            request.getStatus() == null ? null : request.getStatus().name(),
            request.getLaaReference(),
            schemaVersion,
            request.getApplicationType() == null
                ? ApplicationType.APPLY.name()
                : request.getApplicationType().name(),
            parsed.submittedAt(),
            parsed.officeCode(),
            parsed.usedDelegatedFunctions(),
            parsed.categoryOfLaw(),
            parsed.matterType());

    try {
      return commandGateway.sendAndWait(command);
    } catch (AggregateStreamCreationException | ConcurrencyException e) {
      submissionRepository.deleteById(contentId);
      DuplicateApplyApplicationIdException ex =
          new DuplicateApplyApplicationIdException(applyApplicationId);
      ex.initCause(e);
      throw ex;
    }
  }

  private String schemaName(ApplicationCreateRequest request) {
    return request.getApplicationType() == ApplicationType.CCS
        ? "CssApplication.json"
        : "ApplyApplication.json";
  }

  private List<ApplicationIndividual> toIndividuals(List<IndividualCreateRequest> individuals) {
    if (individuals == null) {
      return List.of();
    }
    return individuals.stream()
        .map(
            individual ->
                new ApplicationIndividual(
                    UUID.randomUUID(),
                    individual.getFirstName(),
                    individual.getLastName(),
                    individual.getDateOfBirth(),
                    individual.getDetails(),
                    individual.getType() == null ? null : individual.getType().name()))
        .toList();
  }

  private List<ApplicationProceeding> toProceedings(List<Proceeding> proceedings) {
    return proceedings.stream()
        .map(
            proceeding ->
                new ApplicationProceeding(
                    UUID.randomUUID(),
                    proceeding.getId() == null ? null : proceeding.getId().toString(),
                    proceeding.getDescription(),
                    Boolean.TRUE.equals(proceeding.getLeadProceeding()),
                    proceeding))
        .toList();
  }
}
