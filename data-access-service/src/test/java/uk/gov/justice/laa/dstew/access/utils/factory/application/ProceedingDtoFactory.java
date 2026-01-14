package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.ProceedingDto;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class ProceedingDtoFactory
    extends BaseFactory<ProceedingDto, ProceedingDto.ProceedingDtoBuilder> {


  public ProceedingDtoFactory() {
    super(ProceedingDto::toBuilder, ProceedingDto.ProceedingDtoBuilder::build);
  }

  @Override
  public ProceedingDto createDefault() {
    return ProceedingDto
        .builder()
        .id(UUID.randomUUID())
        .categoryOfLaw(CategoryOfLaw.FAMILY)
        .matterType(MatterType.SCA)
        .leadProceeding(true)
        .useDelegatedFunctions(true)
        .build();
  }

  @Override
  public List<ProceedingDto> createMultipleDefault(int count) {
    List<ProceedingDto> proceedingDetails = new ArrayList<>();
    proceedingDetails.add(createDefault());
    for (int i = 0; i < count - 1; i++) {
      proceedingDetails.add(createDefault().toBuilder().id(UUID.randomUUID()).leadProceeding(false).build());
    }
    return proceedingDetails;
  }
}