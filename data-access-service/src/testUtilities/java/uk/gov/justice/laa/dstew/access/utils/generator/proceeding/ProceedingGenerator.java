package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.util.UUID;

public class ProceedingGenerator extends BaseGenerator<Proceeding, Proceeding.ProceedingBuilder> {
    public ProceedingGenerator() {
        super(Proceeding::toBuilder, Proceeding.ProceedingBuilder::build);
    }

    @Override
    public Proceeding createDefault() {
        return Proceeding.builder()
                .id(UUID.randomUUID())
                .categoryOfLaw(String.valueOf(CategoryOfLaw.FAMILY))
                .matterType(String.valueOf(MatterType.SCA))
                .leadProceeding(true)
                .usedDelegatedFunctions(true)
                .description("The description")
                .build();
    }
}

