package uk.gov.justice.laa.dstew.access.utils.factory.refusal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.RefusalDetails;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class RefusalDetailsFactory extends BaseFactory<RefusalDetails, RefusalDetails.Builder> {

    public RefusalDetailsFactory() {
        super(RefusalDetails::toBuilder, RefusalDetails.Builder::build);
    }

    @Override
    public RefusalDetails createDefault() {
        return RefusalDetails.builder()
                .justification("Justification")
                .reason("Reason")
                .build();
    }
}