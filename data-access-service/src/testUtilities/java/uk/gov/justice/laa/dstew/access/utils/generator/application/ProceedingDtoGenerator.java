package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.ProceedingDto;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.util.UUID;

public class ProceedingDtoGenerator extends BaseGenerator<ProceedingDto, ProceedingDto.ProceedingDtoBuilder> {
    public ProceedingDtoGenerator() {
        super(ProceedingDto::toBuilder, ProceedingDto.ProceedingDtoBuilder::build);
    }

    @Override
    public ProceedingDto createDefault() {
        return ProceedingDto.builder()
                .id(UUID.randomUUID())
                .categoryOfLaw(CategoryOfLaw.FAMILY)
                .matterType(MatterType.SCA)
                .leadProceeding(true)
                .usedDelegatedFunctions(true)
                .build();
    }
}

