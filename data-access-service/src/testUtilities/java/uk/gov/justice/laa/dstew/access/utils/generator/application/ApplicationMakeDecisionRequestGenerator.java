package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.MakeDecisionProceedingGenerator;
import java.util.List;
import java.util.UUID;

public class ApplicationMakeDecisionRequestGenerator extends BaseGenerator<MakeDecisionRequest, MakeDecisionRequest.Builder> {
    private final MakeDecisionProceedingGenerator makeDecisionProceedingGenerator = new MakeDecisionProceedingGenerator();

    public ApplicationMakeDecisionRequestGenerator() {
        super(MakeDecisionRequest::toBuilder, MakeDecisionRequest.Builder::build);
    }

    @Override
    public MakeDecisionRequest createDefault() {
        return MakeDecisionRequest.builder()
                .applicationStatus(ApplicationStatus.APPLICATION_SUBMITTED)
                .overallDecision(DecisionStatus.PARTIALLY_GRANTED)
                .userId(UUID.randomUUID())
                .proceedings(List.of(makeDecisionProceedingGenerator.createDefault()))
                .build();
    }
}

