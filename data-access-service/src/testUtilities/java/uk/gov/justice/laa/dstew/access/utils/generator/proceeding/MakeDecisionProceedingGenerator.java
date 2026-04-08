package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionDetailsGenerator;
import java.util.UUID;

public class MakeDecisionProceedingGenerator extends BaseGenerator<MakeDecisionProceedingRequest, MakeDecisionProceedingRequest.Builder> {
    private final MeritsDecisionDetailsGenerator meritsDecisionDetailsGenerator = new MeritsDecisionDetailsGenerator();

    public MakeDecisionProceedingGenerator() {
        super(MakeDecisionProceedingRequest::toBuilder, MakeDecisionProceedingRequest.Builder::build);
    }

    @Override
    public MakeDecisionProceedingRequest createDefault() {
        return MakeDecisionProceedingRequest.builder()
                .proceedingId(UUID.randomUUID())
                .meritsDecision(meritsDecisionDetailsGenerator.createDefault())
                .build();
    }
}

