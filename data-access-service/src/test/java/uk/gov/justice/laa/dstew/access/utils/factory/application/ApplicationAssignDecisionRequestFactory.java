package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.AssignDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.ProceedingDetailsFactory;

@Profile("unit-test")
@Component
public class ApplicationAssignDecisionRequestFactory extends BaseFactory<AssignDecisionRequest, AssignDecisionRequest.Builder> {

    @Autowired
    private ProceedingDetailsFactory proceedingDetailsFactory;

    public ApplicationAssignDecisionRequestFactory() {
        super(AssignDecisionRequest::toBuilder, AssignDecisionRequest.Builder::build);
    }

    @Override
    public AssignDecisionRequest createDefault() {
        return AssignDecisionRequest.builder()
                .applicationStatus(ApplicationStatus.IN_PROGRESS)
                .overallDecision(DecisionStatus.REFUSED)
                .userId(UUID.randomUUID())
                .proceedings(List.of(proceedingDetailsFactory.createDefault()))
                .build();
    }
}
