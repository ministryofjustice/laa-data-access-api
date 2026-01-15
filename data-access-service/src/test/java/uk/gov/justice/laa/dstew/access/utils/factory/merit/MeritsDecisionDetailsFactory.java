package uk.gov.justice.laa.dstew.access.utils.factory.merit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetails;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.refusal.RefusalDetailsFactory;

@Profile("unit-test")
@Component
public class MeritsDecisionDetailsFactory extends BaseFactory<MeritsDecisionDetails, MeritsDecisionDetails.Builder>  {

    @Autowired
    private RefusalDetailsFactory refusalDetailsFactory;

    public MeritsDecisionDetailsFactory() {
        super(MeritsDecisionDetails::toBuilder, MeritsDecisionDetails.Builder::build);
    }

    @Override
    public MeritsDecisionDetails createDefault() {
        return MeritsDecisionDetails.builder()
                .decision(MeritsDecisionStatus.REFUSED)
                .refusal(refusalDetailsFactory.createDefault())
                .build();
    }
}
