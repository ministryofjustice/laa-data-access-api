package uk.gov.justice.laa.dstew.access.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.BooleanUtils;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitation;

/**
 * Mapper interface.
 * All mapping operations are performed safely, throwing an
 * {@link IllegalArgumentException} if JSON conversion fails.
 */
@Mapper(componentModel = "spring")
public interface ProceedingMapper {


  /**
   * Converts a {@link Proceeding} model into a new {@link ProceedingEntity}.
   *
   * @param proceeding    the proceeding
   * @param applicationId the application id
   * @return ProceedingEntity or null
   */
  default ProceedingEntity toProceedingEntity(Proceeding proceeding, UUID applicationId) {
    if (proceeding == null) {
      return null;
    }
    ProceedingEntity proceedingEntity = new ProceedingEntity();
    proceedingEntity.setApplicationId(applicationId);
    proceedingEntity.setApplyProceedingId(proceeding.getId());
    proceedingEntity.setLead(BooleanUtils.isTrue(proceeding.getLeadProceeding()));
    proceedingEntity.setDescription(proceeding.getDescription());
    proceedingEntity.setProceedingContent(MapperUtil.getObjectMapper().convertValue(proceeding, Map.class));
    return proceedingEntity;
  }

  /**
   * Converts a {@link Proceeding} model into a new {@link ProceedingEntity}.
   *
   * @param proceedingEntity    the proceeding entity
   * @return Proceeding or null
   */
  default ApplicationProceedingResponse toApplicationProceeding(ProceedingEntity proceedingEntity) {
    if (proceedingEntity == null) {
      return null;
    }
    Proceeding proceeding = MapperUtil.getObjectMapper()
            .convertValue(proceedingEntity.getProceedingContent(), Proceeding.class);

    ApplicationProceedingResponse applicationProceedingResponse = new ApplicationProceedingResponse();
    applicationProceedingResponse.setProceedingId(proceedingEntity.getId());
    applicationProceedingResponse.setProceedingDescription(proceedingEntity.getDescription());
    applicationProceedingResponse.setProceedingType(proceeding.getMeaning());
    applicationProceedingResponse.setUsedDelegatedFunctionsOn(proceeding.getUsedDelegatedFunctionsOn());
    applicationProceedingResponse.setCategoryOfLaw(proceeding.getCategoryOfLaw());
    applicationProceedingResponse.setMatterType(proceeding.getMatterType());
    applicationProceedingResponse.setLevelOfService(proceeding.getSubstantiveLevelOfServiceName());
    applicationProceedingResponse.setSubstantiveCostLimitation(proceeding.getSubstantiveCostLimitation());
    if (proceeding.getScopeLimitations() != null) {
      List<ScopeLimitation> scopeLimitations = new ArrayList<>();
      proceeding.getScopeLimitations().forEach(scopeLimitationMap -> {
        ScopeLimitation scopeLimitation = ScopeLimitation.builder()
            .scopeLimitation(scopeLimitationMap.get("meaning") != null
                ? scopeLimitationMap.get("meaning").toString()
                : null)
            .scopeDescription(scopeLimitationMap.get("description") != null
                ? scopeLimitationMap.get("description").toString()
                : null)
            .build();
        scopeLimitations.add(scopeLimitation);
      });
      applicationProceedingResponse.setScopeLimitations(scopeLimitations);
    }

    return applicationProceedingResponse;
  }
}