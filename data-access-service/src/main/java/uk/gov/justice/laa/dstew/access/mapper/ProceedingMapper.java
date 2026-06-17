package uk.gov.justice.laa.dstew.access.mapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.BooleanUtils;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.InvolvedChild;
import uk.gov.justice.laa.dstew.access.model.InvolvedChildResponse;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.model.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.model.ProceedingMerits;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.utils.EnumParsingUtils;

/**
 * Mapper interface. All mapping operations are performed safely, throwing an {@link
 * IllegalArgumentException} if JSON conversion fails.
 */
@Mapper(componentModel = "spring")
public interface ProceedingMapper {

  /**
   * Converts a {@link Proceeding} model into a new {@link ProceedingEntity}. The applicationId is
   * managed by JPA via the @JoinColumn on ApplicationEntity.proceedings.
   *
   * @param proceeding the proceeding
   * @return ProceedingEntity or null
   */
  default ProceedingEntity toProceedingEntity(Proceeding proceeding) {
    if (proceeding == null) {
      return null;
    }
    ProceedingEntity proceedingEntity = new ProceedingEntity();
    proceedingEntity.setApplyProceedingId(proceeding.getId());
    proceedingEntity.setLead(BooleanUtils.isTrue(proceeding.getLeadProceeding()));
    proceedingEntity.setDescription(proceeding.getDescription());
    proceedingEntity.setProceedingContent(
        MapperUtil.getObjectMapper().convertValue(proceeding, Map.class));
    return proceedingEntity;
  }

  /**
   * Converts a {@link Proceeding} and an {@code applicationId} into a {@link ProceedingEntity}.
   *
   * @param proceeding the proceeding model
   * @param applicationId ignored — applicationId is now managed by JPA
   * @return a new {@link ProceedingEntity}
   * @deprecated Use {@link #toProceedingEntity(Proceeding)} instead. applicationId is now managed
   *     by JPA via the @JoinColumn on ApplicationEntity.proceedings.
   */
  @Deprecated
  default ProceedingEntity toProceedingEntity(Proceeding proceeding, java.util.UUID applicationId) {
    return toProceedingEntity(proceeding);
  }

  /**
   * Converts a {@link Proceeding} model into a new {@link ProceedingEntity}.
   *
   * @param proceedingEntity the proceeding entity
   * @return Proceeding or null
   */
  default ApplicationProceedingResponse toApplicationProceeding(ProceedingEntity proceedingEntity) {
    if (proceedingEntity == null) {
      return null;
    }
    Proceeding proceeding =
        MapperUtil.getObjectMapper()
            .convertValue(proceedingEntity.getProceedingContent(), Proceeding.class);

    ApplicationProceedingResponse applicationProceedingResponse =
        new ApplicationProceedingResponse();
    applicationProceedingResponse.setProceedingId(proceedingEntity.getId());
    applicationProceedingResponse.setProceedingDescription(proceedingEntity.getDescription());
    applicationProceedingResponse.setProceedingType(proceeding.getMeaning());
    applicationProceedingResponse.setDelegatedFunctionsDate(
        proceeding.getUsedDelegatedFunctionsOn());
    applicationProceedingResponse.setCategoryOfLaw(
        EnumParsingUtils.convertToCategoryOfLaw(proceeding.getCategoryOfLawEnum()));
    applicationProceedingResponse.setMatterType(
        EnumParsingUtils.convertToMatterType(proceeding.getMatterTypeEnum()));
    applicationProceedingResponse.setLevelOfService(
        proceeding.getSubstantiveLevelOfServiceNameEnum());
    applicationProceedingResponse.setSubstantiveCostLimitation(
        proceeding.getSubstantiveCostLimitation());
    if (proceeding.getScopeLimitations() != null) {
      List<ScopeLimitationResponse> scopeLimitations =
          proceeding.getScopeLimitations().stream()
              .map(
                  scopeLimitationMap -> {
                    Object meaningObj = scopeLimitationMap.getOrDefault("meaning", null);
                    Object descriptionObj = scopeLimitationMap.getOrDefault("description", null);

                    return ScopeLimitationResponse.builder()
                        .scopeLimitation(meaningObj != null ? meaningObj.toString() : null)
                        .scopeDescription(descriptionObj != null ? descriptionObj.toString() : null)
                        .build();
                  })
              .toList();
      applicationProceedingResponse.setScopeLimitations(scopeLimitations);
    }

    return applicationProceedingResponse;
  }

  /**
   * Converts a {@link ProceedingEntity} into an {@link ApplicationProceedingResponse}, enriched
   * with involved children resolved from the application content.
   *
   * @param proceedingEntity the proceeding entity
   * @param proceedingMeritsList the list of proceedingMerits from applicationContent
   * @param involvedChildren the list of involvedChildren from applicationMerits
   * @return ApplicationProceedingResponse or null
   */
  default ApplicationProceedingResponse toApplicationProceeding(
      ProceedingEntity proceedingEntity,
      List<ProceedingMerits> proceedingMeritsList,
      List<InvolvedChild> involvedChildren) {

    ApplicationProceedingResponse response = toApplicationProceeding(proceedingEntity);
    if (response == null) {
      return null;
    }

    UUID applyProceedingId = proceedingEntity.getApplyProceedingId();
    if (applyProceedingId == null
        || proceedingMeritsList == null
        || proceedingMeritsList.isEmpty()) {
      response.setInvolvedChildren(Collections.emptyList());
      return response;
    }

    List<InvolvedChildResponse> resolvedChildren =
        proceedingMeritsList.stream()
            .filter(pm -> applyProceedingId.equals(pm.getProceedingId()))
            .findFirst()
            .map(pm -> resolveInvolvedChildren(pm.getProceedingLinkedChildren(), involvedChildren))
            .orElse(Collections.emptyList());

    response.setInvolvedChildren(resolvedChildren);
    return response;
  }

  private static List<InvolvedChildResponse> resolveInvolvedChildren(
      List<ProceedingLinkedChild> linkedChildren, List<InvolvedChild> allInvolvedChildren) {

    if (linkedChildren == null || linkedChildren.isEmpty()) {
      return Collections.emptyList();
    }
    if (allInvolvedChildren == null || allInvolvedChildren.isEmpty()) {
      return Collections.emptyList();
    }

    return linkedChildren.stream()
        .map(ProceedingLinkedChild::getInvolvedChildId)
        .filter(Objects::nonNull)
        .flatMap(
            id ->
                allInvolvedChildren.stream()
                    .filter(child -> id.equals(child.getId()))
                    .findFirst()
                    .stream())
        .map(
            child ->
                new InvolvedChildResponse()
                    .fullName(child.getFullName())
                    .dateOfBirth(child.getDateOfBirth()))
        .toList();
  }
}
