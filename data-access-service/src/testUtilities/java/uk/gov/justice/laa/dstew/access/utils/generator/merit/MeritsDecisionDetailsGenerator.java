package uk.gov.justice.laa.dstew.access.utils.generator.merit;

import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetails;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class MeritsDecisionDetailsGenerator extends BaseGenerator<MeritsDecisionDetails, MeritsDecisionDetails.Builder> {

    public MeritsDecisionDetailsGenerator() {
        super(MeritsDecisionDetails::toBuilder, MeritsDecisionDetails.Builder::build);
    }

    @Override
    public MeritsDecisionDetails createDefault() {
        return MeritsDecisionDetails.builder()
                .decision(MeritsDecisionStatus.REFUSED)
                .build();
    }
}

