package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Profile("unit-test")
@Component
public class ProceedingsEntityFactory extends BaseFactory<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder> {

    public ProceedingsEntityFactory() {
        super(ProceedingEntity::toBuilder, ProceedingEntity.ProceedingEntityBuilder::build);
    }

    @Override
    public ProceedingEntity createDefault() {
        return ProceedingEntity.builder()
                .id(UUID.randomUUID())
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