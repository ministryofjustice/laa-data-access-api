package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionDetailsGenerator;
import java.util.UUID;

public class MakeDecisionProceedingGenerator extends BaseGenerator<MakeDecisionProceeding, MakeDecisionProceeding.Builder> {
    private final MeritsDecisionDetailsGenerator meritsDecisionDetailsGenerator = new MeritsDecisionDetailsGenerator();

    public MakeDecisionProceedingGenerator() {
        super(MakeDecisionProceeding::toBuilder, MakeDecisionProceeding.Builder::build);
    }

    @Override
    public MakeDecisionProceeding createDefault() {
        return MakeDecisionProceeding.builder()
                .proceedingId(UUID.randomUUID())
                .meritsDecision(meritsDecisionDetailsGenerator.createDefault())
                .build();
    }
}

