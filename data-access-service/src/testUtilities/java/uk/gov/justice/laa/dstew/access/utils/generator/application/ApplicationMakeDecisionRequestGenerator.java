package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.List;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.MakeDecisionProceedingGenerator;

public class ApplicationMakeDecisionRequestGenerator extends BaseGenerator<MakeDecisionRequest, MakeDecisionRequest.Builder> {
    private final MakeDecisionProceedingGenerator makeDecisionProceedingGenerator = new MakeDecisionProceedingGenerator();

    public ApplicationMakeDecisionRequestGenerator() {
        super(MakeDecisionRequest::toBuilder, MakeDecisionRequest.Builder::build);
    }

    @Override
    public MakeDecisionRequest createDefault() {
        return MakeDecisionRequest.builder()
            .overallDecision(DecisionStatus.REFUSED)
                .proceedings(List.of(makeDecisionProceedingGenerator.createDefault()))
                .applicationVersion(0L)
                .build();
    }
}

