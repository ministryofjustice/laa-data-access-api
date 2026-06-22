package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.LinkedApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.LinkedApplication;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Orchestrates the full create-application flow. */
@RequiredArgsConstructor
public class CreateApplicationUseCase {

  private final ApplicationGateway applicationGateway;
  private final LinkedApplicationGateway linkedApplicationGateway;
  private final ApplicationContentParser applicationContentParser;
  private final CreateApplicationDomainMapper domainMapper;
  private final SaveDomainEventService saveDomainEventService;
  private final JsonSchemaValidator jsonSchemaValidator;

  /**
   * Executes the createApplication use case.
   *
   * @param command the command carrying all fields from the HTTP request
   * @return the saved {@link ApplicationDomain} with database-generated fields populated
   */
  @AllowApiCaseworker
  @Transactional
  public ApplicationDomain execute(CreateApplicationCommand command) {

    // 1. Validate against JSON schema
    String formType =
        switch (command.applicationType()) {
          case "CCS" -> "CssApplication.json";
          default -> "ApplyApplication.json";
        };
    jsonSchemaValidator.validate(command.applicationContent(), formType, command.schemaVersion());

    // 3. Validate and parse application content
    ParsedAppContentDetails parsed = applicationContentParser.parse(command.applicationContent());

    // 4. Duplicate-check BEFORE save
    if (applicationGateway.existsByApplyApplicationId(parsed.applyApplicationId())) {
      throw new ValidationException(
          List.of(
              "Application already exists for Apply Application Id: "
                  + parsed.applyApplicationId()));
    }

    // 5. Build domain (pre-save; id and createdAt are null) then persist application
    ApplicationDomain savedApplication =
        applicationGateway.save(domainMapper.toApplicationDomain(command, parsed));

    // 6. Link to lead application if applicable (after save — requires savedApplication.id())
    linkToLeadApplicationIfApplicable(parsed.allLinkedApplications(), savedApplication);

    // 7. Publish domain event (uses pre-serialised request from command)
    saveDomainEventService.saveCreateApplicationDomainEvent(
        savedApplication, command.serialisedRequest());

    return savedApplication;
  }

  private void linkToLeadApplicationIfApplicable(
      List<LinkedApplication> allLinkedApplications, ApplicationDomain savedDomain) {
    getLeadApplicationId(allLinkedApplications)
        .ifPresent(leadAppId -> linkedApplicationGateway.link(leadAppId, savedDomain.id()));
  }

  private Optional<UUID> getLeadApplicationId(List<LinkedApplication> allLinkedApplications) {

    if (allLinkedApplications == null || allLinkedApplications.isEmpty()) {
      return Optional.empty();
    }

    UUID leadApplyId = allLinkedApplications.getFirst().getLeadApplicationId();

    if (leadApplyId == null) {
      return Optional.empty();
    }

    List<UUID> associatedIds =
        allLinkedApplications.stream()
            .map(LinkedApplication::getAssociatedApplicationId)
            .filter(uuid -> !uuid.equals(leadApplyId))
            .toList();
    List<UUID> missing = applicationGateway.findMissingApplyApplicationIds(associatedIds);
    if (!missing.isEmpty()) {
      throw new ResourceNotFoundException(
          "No linked application found with associated apply ids: " + missing);
    }

    ApplicationDomain leadDomain =
        applicationGateway
            .findLeadByApplyApplicationId(leadApplyId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Linking failed > Lead application not found, ID: " + leadApplyId));

    return Optional.of(leadDomain.id());
  }
}
