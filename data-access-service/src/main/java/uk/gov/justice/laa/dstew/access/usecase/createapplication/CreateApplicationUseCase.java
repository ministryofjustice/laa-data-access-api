package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import static uk.gov.justice.laa.dstew.access.usecase.shared.ApplicationConstants.APPLICATION_SCHEMA_VERSION;

import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplication;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.ApplicationContentParserService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ProceedingGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.validation.UseCaseValidations;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Use case for creating an application. Wired via CreateApplicationConfig (no @Component). */
@Transactional
public class CreateApplicationUseCase {

  private final ApplicationGateway applicationGateway;
  private final ProceedingGateway proceedingGateway;
  private final DomainEventGateway domainEventGateway;
  private final ApplicationContentParserService contentParser;
  private final ObjectMapper objectMapper;

  /**
   * Constructs the use case with required dependencies.
   *
   * @param applicationGateway gateway for application persistence
   * @param proceedingGateway gateway for proceeding persistence
   * @param domainEventGateway gateway for domain event publishing
   * @param contentParser service for parsing application content
   * @param objectMapper Jackson mapper for extracting proceedings from content map
   */
  public CreateApplicationUseCase(
      ApplicationGateway applicationGateway,
      ProceedingGateway proceedingGateway,
      DomainEventGateway domainEventGateway,
      ApplicationContentParserService contentParser,
      ObjectMapper objectMapper) {
    this.applicationGateway = applicationGateway;
    this.proceedingGateway = proceedingGateway;
    this.domainEventGateway = domainEventGateway;
    this.contentParser = contentParser;
    this.objectMapper = objectMapper;
  }

  /**
   * Executes the create application use case.
   *
   * @param command the creation command
   * @return the UUID of the created application
   */
  @EnforceRole(anyOf = RequiredRole.API_CASEWORKER)
  public UUID execute(CreateApplicationCommand command) {
    var details = contentParser.parseFromMap(command.applicationContent());

    if (applicationGateway.existsByApplyApplicationId(details.applyApplicationId())) {
      throw new ValidationException(
          List.of(
              "Application already exists for Apply Application Id: "
                  + details.applyApplicationId()));
    }

    if (details.allLinkedApplications() != null) {
      List<UUID> associatedIds =
          details.allLinkedApplications().stream()
              .map(LinkedApplication::associatedApplicationId)
              .filter(id -> !id.equals(details.applyApplicationId()))
              .toList();
      UseCaseValidations.checkApplicationIdList(associatedIds);

      List<UUID> missingIds =
          associatedIds.stream()
              .filter(id -> !applicationGateway.existsByApplyApplicationId(id))
              .toList();
      if (!missingIds.isEmpty()) {
        throw new ResourceNotFoundException(
            "No linked application found with associated apply ids: " + missingIds);
      }
    }

    ApplicationDomain domain =
        ApplicationDomain.builder()
            .status(command.status())
            .laaReference(command.laaReference())
            .officeCode(details.officeCode())
            .applyApplicationId(details.applyApplicationId())
            .usedDelegatedFunctions(details.usedDelegatedFunctions())
            .categoryOfLaw(details.categoryOfLaw())
            .matterType(details.matterType())
            .submittedAt(details.submittedAt())
            .applicationContent(command.applicationContent())
            .individuals(command.individuals())
            .schemaVersion(APPLICATION_SCHEMA_VERSION)
            .build();

    ApplicationDomain saved = applicationGateway.save(domain);

    linkToLeadIfApplicable(details, saved);

    List<ProceedingDomain> proceedings =
        buildProceedingDomains(command.applicationContent(), saved.id());
    proceedingGateway.saveAll(saved.id(), proceedings);

    domainEventGateway.saveCreatedEvent(saved, command.serialisedRequest());

    return saved.id();
  }

  private void linkToLeadIfApplicable(
      uk.gov.justice.laa.dstew.access.domain.ParsedAppContentDetails details,
      ApplicationDomain saved) {
    if (details.allLinkedApplications() == null || details.allLinkedApplications().isEmpty()) {
      return;
    }
    UUID leadApplyId = details.allLinkedApplications().getFirst().leadApplicationId();
    if (leadApplyId == null) {
      return;
    }
    ApplicationDomain lead =
        applicationGateway
            .findByApplyApplicationId(leadApplyId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Linking failed > Lead application not found, ID: " + leadApplyId));
    applicationGateway.addLinkedApplication(lead, saved);
  }

  @SuppressWarnings("unchecked")
  private List<ProceedingDomain> buildProceedingDomains(
      Map<String, Object> contentMap, UUID applicationId) {
    Object proceedingsRaw = contentMap.get("proceedings");
    if (proceedingsRaw == null) {
      return Collections.emptyList();
    }
    List<Map<String, Object>> proceedingMaps =
        objectMapper.convertValue(
            proceedingsRaw, new TypeReference<List<Map<String, Object>>>() {});
    if (proceedingMaps == null || proceedingMaps.isEmpty()) {
      return Collections.emptyList();
    }
    return proceedingMaps.stream()
        .map(
            p -> {
              boolean isLead = Boolean.TRUE.equals(p.get("leadProceeding"));
              return new ProceedingDomain(applicationId, isLead, p);
            })
        .toList();
  }
}
