package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.merit.MeritsDecisionDetailsFactory;
import java.util.UUID;

@Profile("unit-test")
@Component
public class MakeDecisionProceedingFactory extends BaseFactory<MakeDecisionProceeding, MakeDecisionProceeding.Builder> {

    @Autowired
    private MeritsDecisionDetailsFactory meritsDecisionDetailsFactory;

    public MakeDecisionProceedingFactory() {
        super(MakeDecisionProceeding::toBuilder, MakeDecisionProceeding.Builder::build);
    }

    @Override
    public MakeDecisionProceeding createDefault() {
        return MakeDecisionProceeding.builder()
                .proceedingId(UUID.randomUUID())
                .meritsDecision(meritsDecisionDetailsFactory.createDefault())
                .build();
    }
}