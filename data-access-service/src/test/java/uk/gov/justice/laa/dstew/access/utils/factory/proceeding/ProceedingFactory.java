package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class ProceedingFactory
    extends BaseFactory<Proceeding, Proceeding.ProceedingBuilder> {


  public ProceedingFactory() {
    super(Proceeding::toBuilder, Proceeding.ProceedingBuilder::build);
  }

  @Override
  public Proceeding createDefault() {
    return Proceeding
        .builder()
        .id(UUID.randomUUID())
        .categoryOfLaw(String.valueOf(CategoryOfLaw.FAMILY))
        .matterType(String.valueOf(MatterType.SPECIAL_CHILDREN_ACT))
        .leadProceeding(true)
        .usedDelegatedFunctions(true)
        .description("The description")
        .build();
  }


  @Override
  public List<Proceeding> createMultipleDefault(int count) {
    List<Proceeding> proceedings = new ArrayList<>();
    proceedings.add(createDefault());
    for (int i = 0; i < count - 1; i++) {
      proceedings.add(createDefault().toBuilder().id(UUID.randomUUID()).leadProceeding(false).build());
    }
    return proceedings;
  }
}