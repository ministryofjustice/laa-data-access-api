package uk.gov.justice.laa.dstew.access.utils.generator.application;

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
                .categoryOfLaw("Family")
                .matterType("SCA")
                .leadProceeding(true)
                .usedDelegatedFunctions(true)
                .build();
    }
}

