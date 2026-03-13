package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ProceedingsEntityGenerator extends BaseGenerator<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder> {
  public ProceedingsEntityGenerator() {
    super(ProceedingEntity::toBuilder, ProceedingEntity.ProceedingEntityBuilder::build);
  }

    @Override
    public ProceedingEntity createDefault() {
        return ProceedingEntity.builder()
                .applyProceedingId(UUID.randomUUID())
                .description("description")
                .proceedingContent(Map.of(
                        "meaning", "hearing",
                        "matterType", "SPECIAL_CHILDREN_ACT",
                        "categoryOfLaw", "Family",
                        "usedDelegatedFunctionsOn", "2025-05-06",
                        "substantiveCostLimitation", "23.45",
                        "substantiveLevelOfServiceName", "service",
                        "scopeLimitations", List.of(
                                Map.of(
                                        "id", "100",
                                        "code", "AB123D",
                                        "meaning", "hearing"
                                )
                        )
                ))
                .build();
    }
}

