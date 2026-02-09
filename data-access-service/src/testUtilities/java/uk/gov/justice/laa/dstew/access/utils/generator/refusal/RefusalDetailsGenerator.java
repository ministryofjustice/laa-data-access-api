package uk.gov.justice.laa.dstew.access.utils.generator.refusal;

import uk.gov.justice.laa.dstew.access.model.RefusalDetails;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class RefusalDetailsGenerator extends BaseGenerator<RefusalDetails, RefusalDetails.Builder> {
    public RefusalDetailsGenerator() {
        super(RefusalDetails::toBuilder, RefusalDetails.Builder::build);
    }

    @Override
    public RefusalDetails createDefault() {
        return RefusalDetails.builder()
                .justification("refusal details justification")
                .reason("refusal details reason")
                .build();
    }
}

