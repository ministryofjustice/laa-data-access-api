package uk.gov.justice.laa.dstew.access.service.usecase;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.model.Application;
import uk.gov.justice.laa.dstew.access.domain.model.Individual;
import uk.gov.justice.laa.dstew.access.domain.port.inbound.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.domain.port.inbound.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.domain.port.outbound.ApplicationPersistencePort;
import uk.gov.justice.laa.dstew.access.domain.port.outbound.DomainEventPort;
import uk.gov.justice.laa.dstew.access.domain.port.outbound.ProceedingsPersistencePort;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.LinkedApplication;
import uk.gov.justice.laa.dstew.access.model.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;
import uk.gov.justice.laa.dstew.access.validation.PayloadValidationService;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Service class for CreateApplication Use Case. */
@RequiredArgsConstructor
@Service
public class CreateApplicationService implements CreateApplicationUseCase {

  private static final int APPLICATION_VERSION = 1;

  private final ApplicationPersistencePort applicationPersistence;
  private final DomainEventPort domainEvents;
  private final ProceedingsPersistencePort proceedingsPersistence;
  private final ApplicationValidations applicationValidations;

  // ── Legacy dependencies kept for the bridge method used by existing tests ──
  private final PayloadValidationService payloadValidationService;
  private final ApplicationContentParserService applicationContentParser;
  private final ObjectMapper objectMapper;

  /**
   * Legacy entry point retained so that existing tests (which call with an {@link
   * ApplicationCreateRequest}) continue to pass without modification. Converts the API request into
   * a {@link CreateApplicationCommand} and delegates to the hexagonal method.
   *
   * @param req the API-layer create request
   * @return UUID of the created application
   * @deprecated Use {@link #createApplication(CreateApplicationCommand)} via the controller instead
   */
  @Deprecated
  @AllowApiCaseworker
  @Transactional
  public UUID createApplication(final ApplicationCreateRequest req) {
    ApplicationContent applicationContent =
        payloadValidationService.convertAndValidate(
            req.getApplicationContent(), ApplicationContent.class);

    ParsedAppContentDetails parsedContent =
        applicationContentParser.normaliseApplicationContentDetails(applicationContent);

    Set<Individual> individuals =
        req.getIndividuals() == null
            ? Set.of()
            : req.getIndividuals().stream()
                .map(
                    ind ->
                        Individual.builder()
                            .firstName(ind.getFirstName())
                            .lastName(ind.getLastName())
                            .dateOfBirth(ind.getDateOfBirth())
                            .individualContent(ind.getDetails())
                            .type(ind.getType())
                            .build())
                .collect(Collectors.toSet());

    CreateApplicationCommand command =
        CreateApplicationCommand.builder()
            .status(req.getStatus())
            .laaReference(req.getLaaReference())
            .applicationContent(req.getApplicationContent())
            .individuals(individuals)
            .parsedContent(parsedContent)
            .linkedApplications(applicationContent.getAllLinkedApplications())
            .build();

    return createApplication(command);
  }

  /**
   * Creates a new application from the given command. This is the primary hexagonal entry point,
   * called by the controller via {@link CreateApplicationUseCase}.
   *
   * @param command pre-validated creation data
   * @return UUID of the created application
   */
  @Override
  @AllowApiCaseworker
  @Transactional
  public UUID createApplication(final CreateApplicationCommand command) {
    Application application = buildApplicationFromCommand(command);
    checkForDuplicateApplication(application.getApplyApplicationId());
    application.setSchemaVersion(APPLICATION_VERSION);

    final Application saved = applicationPersistence.save(application);

    linkToLeadApplicationIfApplicable(command, saved);
    proceedingsPersistence.saveProceedings(command.applicationContent(), saved.getId());
    domainEvents.publishApplicationCreated(saved, command);

    return saved.getId();
  }

  /**
   * Builds a domain {@link Application} from the command, applying parsed content details.
   *
   * @param command the creation command
   * @return a new domain Application
   */
  private Application buildApplicationFromCommand(CreateApplicationCommand command) {
    ParsedAppContentDetails parsed = command.parsedContent();

    @SuppressWarnings("unchecked")
    Map<String, Object> contentAsMap = command.applicationContent();

    return Application.builder()
        .status(command.status())
        .laaReference(command.laaReference())
        .applicationContent(contentAsMap)
        .individuals(command.individuals())
        .applyApplicationId(parsed.applyApplicationId())
        .usedDelegatedFunctions(parsed.usedDelegatedFunctions())
        .categoryOfLaw(parsed.categoryOfLaw())
        .matterType(parsed.matterType())
        .submittedAt(parsed.submittedAt())
        .officeCode(parsed.officeCode())
        .build();
  }

  /**
   * Validates that the application ID is not a duplicate.
   *
   * @param applyApplicationId the UUID of the application to check
   * @throws ValidationException if a duplicate application exists
   */
  private void checkForDuplicateApplication(final UUID applyApplicationId) {
    if (applicationPersistence.existsByApplyApplicationId(applyApplicationId)) {
      throw new ValidationException(
          List.of("Application already exists for Apply Application Id: " + applyApplicationId));
    }
  }

  private void linkToLeadApplicationIfApplicable(
      CreateApplicationCommand command, Application savedApplication) {
    List<LinkedApplication> linkedApplications = command.linkedApplications();
    UUID currentApplyApplicationId = command.parsedContent().applyApplicationId();
    final Optional<Application> leadApplication =
        getLeadApplication(linkedApplications, currentApplyApplicationId);
    leadApplication.ifPresent(
        leadApp -> {
          leadApp.addLinkedApplication(savedApplication);
          applicationPersistence.save(leadApp);
        });
  }

  private Optional<Application> getLeadApplication(
      List<LinkedApplication> linkedApplications, UUID currentApplyApplicationId) {
    final UUID leadApplicationId = getLeadApplicationId(linkedApplications);

    if (linkedApplications != null) {
      List<UUID> associatedIds =
          linkedApplications.stream()
              .map(LinkedApplication::getAssociatedApplicationId)
              .filter(uuid -> !uuid.equals(currentApplyApplicationId))
              .toList();

      checkIfAllAssociatedApplicationsExist(associatedIds);
    }

    if (leadApplicationId == null) {
      return Optional.empty();
    }

    var leadApplication = applicationPersistence.findByApplyApplicationId(leadApplicationId);
    if (leadApplication.isEmpty()) {
      throw new ResourceNotFoundException(
          "Linking failed > Lead application not found, ID: " + leadApplicationId);
    }

    return leadApplication;
  }

  private static UUID getLeadApplicationId(List<LinkedApplication> linkedApplications) {
    return (linkedApplications != null && !linkedApplications.isEmpty())
        ? linkedApplications.getFirst().getLeadApplicationId()
        : null;
  }

  /**
   * Checks that applications exist for all the IDs provided.
   *
   * @param associatedApplyIds collection of apply application IDs to verify
   */
  private void checkIfAllAssociatedApplicationsExist(final List<UUID> associatedApplyIds) {
    applicationValidations.checkApplicationIdList(associatedApplyIds);
    List<UUID> foundApplyAppIds =
        applicationPersistence.findAllByApplyApplicationIdIn(associatedApplyIds).stream()
            .map(Application::getApplyApplicationId)
            .toList();
    if (foundApplyAppIds.size() != associatedApplyIds.size()) {
      List<UUID> remainingIds =
          associatedApplyIds.stream().filter(id -> !foundApplyAppIds.contains(id)).toList();
      String exceptionMsg =
          "No linked application found with associated apply ids: " + remainingIds;
      throw new ResourceNotFoundException(exceptionMsg);
    }
  }
}
