package uk.gov.justice.laa.dstew.access.service.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.command.application.SubmitApplicationCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.query.draft.DraftRecord;
import uk.gov.justice.laa.dstew.access.query.draft.DraftRepository;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionData;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionRecord;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionRepository;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionType;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/**
 * Application-layer entry point for submitting a previously created draft application.
 *
 * <p>Submit takes no body: it seals the draft body already stored in the deletable {@code drafts}
 * table. It reads that raw body, validates and parses it, persists the sealed payload to the
 * immutable {@code submissions} table under a minted {@code contentId}, then dispatches a PII-free
 * {@link SubmitApplicationCommand} carrying only that pointer and non-PII structural metadata. The
 * aggregate transitions {@code DRAFT -> SUBMITTED} and never sees personal data. If the command is
 * rejected, the orphaned submission row is removed.
 */
@Service
public class SubmitApplicationService {

  private final DraftRepository draftRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationContentParser parser;
  private final JsonSchemaValidator jsonSchemaValidator;
  private final SubmissionRepository submissionRepository;
  private final CommandGateway commandGateway;
  private final Clock clock;

  /** Wires the draft store, mapper, parsing, validation, payload store, gateway and clock. */
  public SubmitApplicationService(
      DraftRepository draftRepository,
      ObjectMapper objectMapper,
      ApplicationContentParser parser,
      JsonSchemaValidator jsonSchemaValidator,
      SubmissionRepository submissionRepository,
      CommandGateway commandGateway,
      Clock clock) {
    this.draftRepository = draftRepository;
    this.objectMapper = objectMapper;
    this.parser = parser;
    this.jsonSchemaValidator = jsonSchemaValidator;
    this.submissionRepository = submissionRepository;
    this.commandGateway = commandGateway;
    this.clock = clock;
  }

  /**
   * Seals and submits the stored draft for the given application id.
   *
   * @param applicationId the application whose draft is being submitted
   * @param schemaVersion the JSON schema version to validate the draft content against
   * @return the Apply Application identifier of the submitted application
   */
  public UUID submit(UUID applicationId, int schemaVersion) {
    DraftRecord draft =
        draftRepository
            .findById(applicationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No draft application found with ID: " + applicationId));

    ApplicationCreateRequest request =
        objectMapper.convertValue(draft.getContent(), ApplicationCreateRequest.class);

    jsonSchemaValidator.validate(
        request.getApplicationContent(), schemaName(request), schemaVersion);
    ParsedAppContentDetails parsed = parser.parse(request.getApplicationContent());

    UUID contentId = UUID.randomUUID();

    submissionRepository.save(
        SubmissionRecord.builder()
            .contentId(contentId)
            .applyApplicationId(applicationId)
            .submissionType(SubmissionType.CIVIL_APPLICATION)
            .data(
                new SubmissionData(
                    parsed.applicationContent(),
                    toIndividuals(request.getIndividuals()),
                    toProceedings(parsed.proceedings())))
            .createdAt(Instant.now(clock))
            .build());

    SubmitApplicationCommand command =
        new SubmitApplicationCommand(
            applicationId,
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
    } catch (RuntimeException e) {
      submissionRepository.deleteById(contentId);
      throw e;
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
