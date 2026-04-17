package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullBenefitCheckResult;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullBenefitCheckResultGenerator extends BaseGenerator<FullBenefitCheckResult, FullBenefitCheckResult.FullBenefitCheckResultBuilder> {

    public FullBenefitCheckResultGenerator() {
        super(FullBenefitCheckResult::toBuilder, FullBenefitCheckResult.FullBenefitCheckResultBuilder::build);
    }

    private String randomInstant() {
        return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
    }

    @Override
    public FullBenefitCheckResult createDefault() {
        return FullBenefitCheckResult.builder()
                .id(UUID.randomUUID().toString())
                .legalAidApplicationId(UUID.randomUUID().toString())
                .result(faker.options().option("skipped:no_means_test_required", "no", "yes"))
                .dwpRef(null)
                .createdAt(randomInstant())
                .updatedAt(randomInstant())
                .build();
    }
}

