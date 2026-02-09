package uk.gov.justice.laa.dstew.access.utils.generator.merit;

import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetails;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.refusal.RefusalDetailsGenerator;

public class MeritsDecisionDetailsGenerator extends BaseGenerator<MeritsDecisionDetails, MeritsDecisionDetails.Builder> {
    private final RefusalDetailsGenerator refusalDetailsGenerator = new RefusalDetailsGenerator();

    public MeritsDecisionDetailsGenerator() {
        super(MeritsDecisionDetails::toBuilder, MeritsDecisionDetails.Builder::build);
    }

    @Override
    public MeritsDecisionDetails createDefault() {
        return MeritsDecisionDetails.builder()
                .decision(MeritsDecisionStatus.REFUSED)
                .refusal(refusalDetailsGenerator.createDefault())
                .build();
    }
}

