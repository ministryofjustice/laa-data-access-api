package uk.gov.justice.laa.dstew.access.mapper;

import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.BooleanUtils;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.model.Proceeding;

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
  default ApplicationProceeding toApplicationProceeding(ProceedingEntity proceedingEntity) {
    if (proceedingEntity == null) {
      return null;
    }
    Proceeding proceeding = MapperUtil.getObjectMapper()
            .convertValue(proceedingEntity.getProceedingContent(), Proceeding.class);

    if (proceeding.getAdditionalProceedingsData().get("scopeLimitations") != null) {
      proceeding.setScopeLimitations(Map.of("scopeLimitations", proceeding.getAdditionalProceedingsData().get("scopeLimitations")));
    }

    ApplicationProceeding applicationProceeding = new ApplicationProceeding();
    applicationProceeding.setProceedingId(proceedingEntity.getId());
    applicationProceeding.setProceedingDescription(proceedingEntity.getDescription());
    applicationProceeding.setProceedingType(proceeding.getMeaning());
    applicationProceeding.setUsedDelegatedFunctionsOn(proceeding.getUsedDelegatedFunctionsOn());
    applicationProceeding.setCategoryOfLaw(proceeding.getCategoryOfLaw());
    applicationProceeding.setMatterType(proceeding.getMatterType());
    applicationProceeding.setLevelOfService(proceeding.getSubstantiveLevelOfServiceName());
    applicationProceeding.setSubstantiveCostLimitation(proceeding.getSubstantiveCostLimitation());
    applicationProceeding.setScopeLimitations(proceeding.getScopeLimitations());

    return applicationProceeding;
  }
}