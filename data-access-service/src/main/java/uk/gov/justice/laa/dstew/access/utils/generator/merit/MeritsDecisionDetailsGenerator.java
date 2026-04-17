package uk.gov.justice.laa.dstew.access.utils.generator.merit;

import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class MeritsDecisionDetailsGenerator extends BaseGenerator<MeritsDecisionDetailsRequest, MeritsDecisionDetailsRequest.Builder> {

    public MeritsDecisionDetailsGenerator() {
        super(MeritsDecisionDetailsRequest::toBuilder, MeritsDecisionDetailsRequest.Builder::build);
    }

    @Override
    public MeritsDecisionDetailsRequest createDefault() {
        return MeritsDecisionDetailsRequest.builder()
                .decision(MeritsDecisionStatus.REFUSED)
                .build();
    }
}

