package uk.gov.justice.laa.dstew.access.mapper;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.BooleanUtils;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
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
        EnumParsingUtils.convertToCategoryOfLaw(proceeding.getCategoryOfLaw()));
    applicationProceedingResponse.setMatterType(
        EnumParsingUtils.convertToMatterType(proceeding.getMatterType()));
    applicationProceedingResponse.setLevelOfService(proceeding.getSubstantiveLevelOfServiceName());
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
}
