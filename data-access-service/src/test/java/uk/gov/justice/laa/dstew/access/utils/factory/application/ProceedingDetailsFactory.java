package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;
import uk.gov.justice.laa.dstew.access.model.ProceedingDetails;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class ProceedingDetailsFactory
    extends BaseFactory<ProceedingDetails, ProceedingDetails.ProceedingDetailsBuilder> {


  public ProceedingDetailsFactory() {
    super(ProceedingDetails::toBuilder, ProceedingDetails.ProceedingDetailsBuilder::build);
  }

  @Override
  public ProceedingDetails createDefault() {
    return ProceedingDetails
        .builder()
        .id(UUID.randomUUID())
        .categoryOfLaw(CategoryOfLaw.Family)
        .matterType(MatterType.SCA)
        .leadProceeding(true)
        .useDelegatedFunctions(true)
        .build();
  }

  @Override
  public List<ProceedingDetails> createMultipleDefault(int count) {
    List<ProceedingDetails> proceedingDetails = new ArrayList<>();
    proceedingDetails.add(createDefault());
    for (int i = 0; i < count - 1; i++) {
      proceedingDetails.add(createDefault().toBuilder().id(UUID.randomUUID()).leadProceeding(false).build());
    }
    return proceedingDetails;
  }
}