package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getapplication;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ProceedingDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.InvolvedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.OpponentDetails;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.Proceeding;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ProceedingMerits;

/**
 * Maps application entities to intermediate DB projection DTOs. Responsible for extracting parsed
 * fields and joining proceedings with merits and involved children.
 */
public class GetApplicationGatewayMapper {

  private final ObjectMapper objectMapper;

  /**
   * Constructs the mapper.
   *
   * @param objectMapper object mapper
   */
  public GetApplicationGatewayMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Extracts an application entity into a DB projection. */
  public ApplicationDbProjection toApplicationDbProjection(ApplicationEntity application) {
    ApplicationContent applicationContent =
        toApplicationContent(application.getApplicationContent());
    List<ProceedingMerits> proceedingMerits = toProceedingMerits(applicationContent);
    List<InvolvedChild> involvedChildren = toInvolvedChildren(applicationContent);

    return ApplicationDbProjection.builder()
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
        .version(application.getVersion())
        .officeCode(application.getOfficeCode())
        .submitterEmail(applicationContent != null ? applicationContent.getSubmitterEmail() : null)
        .opponents(toOpponentDetails(applicationContent))
        .proceedings(
            toProceedingDbProjections(
                application.getProceedings(), proceedingMerits, involvedChildren))
        .build();
  }

  private List<ProceedingDbProjection> toProceedingDbProjections(
      Set<ProceedingEntity> proceedings,
      List<ProceedingMerits> proceedingMerits,
      List<InvolvedChild> involvedChildren) {
    if (proceedings == null) {
      return Collections.emptyList();
    }

    return proceedings.stream()
        .map(p -> toProceedingDbProjection(p, proceedingMerits, involvedChildren))
        .toList();
  }

  /** Extracts a proceeding entity into a DB projection with resolved involved children. */
  public ProceedingDbProjection toProceedingDbProjection(
      ProceedingEntity proceedingEntity,
      List<ProceedingMerits> proceedingMerits,
      List<InvolvedChild> involvedChildren) {
    Proceeding proceeding =
        objectMapper.convertValue(proceedingEntity.getProceedingContent(), Proceeding.class);

    return ProceedingDbProjection.builder()
        .proceedingId(proceedingEntity.getId())
        .description(proceedingEntity.getDescription())
        .meritsDecision(
            proceedingEntity.getMeritsDecision() != null
                    && proceedingEntity.getMeritsDecision().getDecision() != null
                ? proceedingEntity.getMeritsDecision().getDecision().name()
                : null)
        .proceedingType(proceeding.getMeaning())
        .categoryOfLaw(proceeding.getCategoryOfLawEnum())
        .matterType(proceeding.getMatterTypeEnum())
        .levelOfService(proceeding.getSubstantiveLevelOfServiceNameEnum())
        .substantiveCostLimitation(proceeding.getSubstantiveCostLimitation())
        .delegatedFunctionsDate(proceeding.getUsedDelegatedFunctionsOn())
        .scopeLimitations(proceeding.getScopeLimitations())
        .involvedChildren(
            resolveInvolvedChildren(
                proceedingEntity.getApplyProceedingId(), proceedingMerits, involvedChildren))
        .build();
  }

  private List<InvolvedChild> resolveInvolvedChildren(
      UUID applyProceedingId,
      List<ProceedingMerits> proceedingMerits,
      List<InvolvedChild> involvedChildren) {
    if (applyProceedingId == null || proceedingMerits == null || proceedingMerits.isEmpty()) {
      return Collections.emptyList();
    }

    return proceedingMerits.stream()
        .filter(merits -> applyProceedingId.equals(merits.getProceedingId()))
        .findFirst()
        .map(
            merits ->
                filterInvolvedChildren(merits.getProceedingLinkedChildren(), involvedChildren))
        .orElse(Collections.emptyList());
  }

  private List<InvolvedChild> filterInvolvedChildren(
      List<ProceedingLinkedChild> linkedChildren, List<InvolvedChild> involvedChildren) {
    if (linkedChildren == null
        || linkedChildren.isEmpty()
        || involvedChildren == null
        || involvedChildren.isEmpty()) {
      return Collections.emptyList();
    }

    return linkedChildren.stream()
        .map(ProceedingLinkedChild::getInvolvedChildId)
        .filter(Objects::nonNull)
        .flatMap(
            childId ->
                involvedChildren.stream()
                    .filter(child -> childId.equals(child.getId()))
                    .findFirst()
                    .stream())
        .toList();
  }

  private List<OpponentDetails> toOpponentDetails(ApplicationContent applicationContent) {
    if (applicationContent == null || applicationContent.getApplicationMerits() == null) {
      return Collections.emptyList();
    }

    ApplicationMerits merits = applicationContent.getApplicationMerits();
    if (merits.getOpponents() == null) {
      return Collections.emptyList();
    }

    return merits.getOpponents();
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
