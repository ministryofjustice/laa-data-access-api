package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

import java.util.UUID;

public class LinkedApplicationSummaryDtoGenerator extends BaseGenerator<LinkedApplicationSummaryDto, LinkedApplicationSummaryDto.LinkedApplicationSummaryDtoBuilder> {

    public LinkedApplicationSummaryDtoGenerator() {
        super(LinkedApplicationSummaryDto::toBuilder, LinkedApplicationSummaryDto.LinkedApplicationSummaryDtoBuilder::build);
    }

    @Override
    public LinkedApplicationSummaryDto createDefault() {
        return LinkedApplicationSummaryDto.builder()
                .applicationId(UUID.randomUUID())
                .laaReference("REF7327")
                .isLead(true)
                .leadApplicationId(UUID.randomUUID())
                .build();
    }
}

