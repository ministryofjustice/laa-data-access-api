package uk.gov.justice.laa.dstew.access.utils.factory.merit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetails;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class MeritsDecisionDetailsFactory extends BaseFactory<MeritsDecisionDetails, MeritsDecisionDetails.Builder>  {

    public MeritsDecisionDetailsFactory() {
        super(MeritsDecisionDetails::toBuilder, MeritsDecisionDetails.Builder::build);
    }

    @Override
    public MeritsDecisionDetails createDefault() {
        return MeritsDecisionDetails.builder()
                .decision(MeritsDecisionStatus.REFUSED)
                .build();
    }
}
