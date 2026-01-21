package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ProceedingDetails;
import uk.gov.justice.laa.dstew.access.model.ProceedingDto;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.merit.MeritsDecisionDetailsFactory;
import java.util.UUID;

@Profile("unit-test")
@Component
public class ProceedingDetailsFactory extends BaseFactory<ProceedingDetails, ProceedingDetails.Builder> {

    @Autowired
    private MeritsDecisionDetailsFactory meritsDecisionDetailsFactory;

    public ProceedingDetailsFactory() {
        super(ProceedingDetails::toBuilder, ProceedingDetails.Builder::build);
    }

    @Override
    public ProceedingDetails createDefault() {
        return ProceedingDetails.builder()
                .proceedingId(UUID.randomUUID())
                .meritsDecision(meritsDecisionDetailsFactory.createDefault())
                .build();
    }
}