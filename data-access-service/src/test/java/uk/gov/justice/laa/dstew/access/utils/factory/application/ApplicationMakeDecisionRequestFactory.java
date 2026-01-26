package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.MakeDecisionProceedingFactory;

@Profile("unit-test")
@Component
public class ApplicationMakeDecisionRequestFactory extends BaseFactory<MakeDecisionRequest, MakeDecisionRequest.Builder> {

    @Autowired
    private MakeDecisionProceedingFactory makeDecisionProceedingFactory;

    public ApplicationMakeDecisionRequestFactory() {
        super(MakeDecisionRequest::toBuilder, MakeDecisionRequest.Builder::build);
    }

    @Override
    public MakeDecisionRequest createDefault() {
        return MakeDecisionRequest.builder()
                .applicationStatus(ApplicationStatus.APPLICATION_SUBMITTED)
                .overallDecision(DecisionStatus.PARTIALLY_GRANTED)
                .userId(UUID.randomUUID())
                .proceedings(List.of(makeDecisionProceedingFactory.createDefault()))
                .build();
    }
}
