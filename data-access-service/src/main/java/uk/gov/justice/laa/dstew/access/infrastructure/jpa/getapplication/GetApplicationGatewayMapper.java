package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getapplication;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationProceedingDomain;
import uk.gov.justice.laa.dstew.access.domain.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.domain.InvolvedChildDomain;
import uk.gov.justice.laa.dstew.access.domain.OpponentDomain;
import uk.gov.justice.laa.dstew.access.domain.ProviderDomain;
import uk.gov.justice.laa.dstew.access.domain.ScopeLimitationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.InvolvedChild;
import uk.gov.justice.laa.dstew.access.model.Opposable;
import uk.gov.justice.laa.dstew.access.model.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.Proceeding;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ProceedingMerits;

/** Maps application entities to the get-application read model. */
public class GetApplicationGatewayMapper {

  private static final String APPLICATION_TYPE_INITIAL = "INITIAL";

  private final ObjectMapper objectMapper;

  /**
   * Constructs the mapper.
   *
   * @param objectMapper object mapper
   */
  public GetApplicationGatewayMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Maps an application entity to the read model returned by the use case.
   *
   * @param application application entity
   * @return application read model
   */
  public ApplicationReadModel toApplicationReadModel(ApplicationEntity application) {
    ApplicationContent applicationContent =
        toApplicationContent(application.getApplicationContent());

    List<ProceedingMerits> proceedingMerits = toProceedingMerits(applicationContent);
    List<InvolvedChild> involvedChildren = toInvolvedChildren(applicationContent);

    return ApplicationReadModel.builder()
        .id(application.getId())
        .status(application.getStatus() != null ? application.getStatus().name() : null)
        .laaReference(application.getLaaReference())
        .updatedAt(application.getUpdatedAt())
        .caseworkerId(
            application.getCaseworker() != null ? application.getCaseworker().getId() : null)
        .submittedAt(application.getSubmittedAt())
        .isLead(application.isLead())
        .usedDelegatedFunctions(application.getUsedDelegatedFunctions())
        .autoGrant(application.getIsAutoGranted())
        .decisionStatus(
            application.getDecision() != null
                    && application.getDecision().getOverallDecision() != null
                ? application.getDecision().getOverallDecision().name()
                : null)
        .applicationType(APPLICATION_TYPE_INITIAL)
        .version(application.getVersion())
        .opponents(toOpponentDomains(applicationContent))
        .provider(toProviderDomain(application.getOfficeCode(), applicationContent))
        .proceedings(
            toApplicationProceedingDomains(
                application.getProceedings(), proceedingMerits, involvedChildren))
        .build();
  }

  private List<ApplicationProceedingDomain> toApplicationProceedingDomains(
      java.util.Set<ProceedingEntity> proceedings,
      List<ProceedingMerits> proceedingMerits,
      List<InvolvedChild> involvedChildren) {
    if (proceedings == null) {
      return Collections.emptyList();
    }

    return proceedings.stream()
        .map(
            proceeding ->
                toApplicationProceedingDomain(proceeding, proceedingMerits, involvedChildren))
        .toList();
  }

  private ApplicationProceedingDomain toApplicationProceedingDomain(
      ProceedingEntity proceedingEntity,
      List<ProceedingMerits> proceedingMerits,
      List<InvolvedChild> involvedChildren) {

    Proceeding proceeding =
        objectMapper.convertValue(proceedingEntity.getProceedingContent(), Proceeding.class);

    return ApplicationProceedingDomain.builder()
        .proceedingId(proceedingEntity.getId())
        .description(proceedingEntity.getDescription())
        .proceedingType(proceeding.getMeaning())
        .categoryOfLaw(proceeding.getCategoryOfLawEnum())
        .matterType(proceeding.getMatterTypeEnum())
        .levelOfService(proceeding.getSubstantiveLevelOfServiceNameEnum())
        .substantiveCostLimitation(proceeding.getSubstantiveCostLimitation())
        .delegatedFunctionsDate(proceeding.getUsedDelegatedFunctionsOn())
        .meritsDecision(
            proceedingEntity.getMeritsDecision() != null
                    && proceedingEntity.getMeritsDecision().getDecision() != null
                ? proceedingEntity.getMeritsDecision().getDecision().name()
                : null)
        .involvedChildren(
            toInvolvedChildDomains(
                proceedingEntity.getApplyProceedingId(), proceedingMerits, involvedChildren))
        .scopeLimitations(toScopeLimitationDomains(proceeding.getScopeLimitations()))
        .build();
  }

  private List<InvolvedChildDomain> toInvolvedChildDomains(
      UUID applyProceedingId,
      List<ProceedingMerits> proceedingMerits,
      List<InvolvedChild> involvedChildren) {
    if (applyProceedingId == null || proceedingMerits.isEmpty()) {
      return Collections.emptyList();
    }

    return proceedingMerits.stream()
        .filter(merits -> applyProceedingId.equals(merits.getProceedingId()))
        .findFirst()
        .map(
            merits ->
                toInvolvedChildDomains(merits.getProceedingLinkedChildren(), involvedChildren))
        .orElse(Collections.emptyList());
  }

  private List<InvolvedChildDomain> toInvolvedChildDomains(
      List<ProceedingLinkedChild> linkedChildren, List<InvolvedChild> involvedChildren) {
    if (linkedChildren == null || linkedChildren.isEmpty() || involvedChildren.isEmpty()) {
      return Collections.emptyList();
    }

    return linkedChildren.stream()
        .map(ProceedingLinkedChild::getInvolvedChildId)
        .filter(Objects::nonNull)
        .flatMap(
            childId ->
                involvedChildren.stream()
                    .filter(involvedChild -> childId.equals(involvedChild.getId()))
                    .findFirst()
                    .stream())
        .map(
            involvedChild ->
                InvolvedChildDomain.builder()
                    .fullName(involvedChild.getFullName())
                    .dateOfBirth(involvedChild.getDateOfBirth())
                    .build())
        .toList();
  }

  private List<ScopeLimitationDomain> toScopeLimitationDomains(
      List<Map<String, Object>> scopeLimitations) {
    if (scopeLimitations == null) {
      return Collections.emptyList();
    }

    return scopeLimitations.stream()
        .map(
            scopeLimitation ->
                ScopeLimitationDomain.builder()
                    .scopeLimitation(
                        scopeLimitation.get("meaning") != null
                            ? scopeLimitation.get("meaning").toString()
                            : null)
                    .scopeDescription(
                        scopeLimitation.get("description") != null
                            ? scopeLimitation.get("description").toString()
                            : null)
                    .build())
        .toList();
  }

  private List<OpponentDomain> toOpponentDomains(ApplicationContent applicationContent) {
    if (applicationContent == null || applicationContent.getApplicationMerits() == null) {
      return Collections.emptyList();
    }

    ApplicationMerits merits = applicationContent.getApplicationMerits();
    if (merits.getOpponents() == null) {
      return Collections.emptyList();
    }

    return merits.getOpponents().stream()
        .map(
            opponentDetails -> {
              Opposable opposable = opponentDetails.getOpposable();
              return OpponentDomain.builder()
                  .opponentType(opponentDetails.getOpposableType())
                  .firstName(opposable != null ? opposable.getFirstName() : null)
                  .lastName(opposable != null ? opposable.getLastName() : null)
                  .organisationName(opposable != null ? opposable.getName() : null)
                  .build();
            })
        .toList();
  }

  private ProviderDomain toProviderDomain(
      String officeCode, ApplicationContent applicationContent) {
    String contactEmail =
        applicationContent != null ? applicationContent.getSubmitterEmail() : null;

    if (officeCode == null && contactEmail == null) {
      return null;
    }

    return ProviderDomain.builder().officeCode(officeCode).contactEmail(contactEmail).build();
  }

  private ApplicationContent toApplicationContent(Map<String, Object> applicationContent) {
    if (applicationContent == null) {
      return null;
    }

    return objectMapper.convertValue(applicationContent, ApplicationContent.class);
  }

  private List<ProceedingMerits> toProceedingMerits(ApplicationContent applicationContent) {
    if (applicationContent == null || applicationContent.getProceedingMerits() == null) {
      return Collections.emptyList();
    }

    return applicationContent.getProceedingMerits();
  }

  private List<InvolvedChild> toInvolvedChildren(ApplicationContent applicationContent) {
    if (applicationContent == null || applicationContent.getApplicationMerits() == null) {
      return Collections.emptyList();
    }

    List<InvolvedChild> involvedChildren =
        applicationContent.getApplicationMerits().getInvolvedChildren();
    return involvedChildren != null ? involvedChildren : Collections.emptyList();
  }
}
