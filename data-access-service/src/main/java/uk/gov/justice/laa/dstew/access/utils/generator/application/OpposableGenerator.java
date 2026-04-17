package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.Opposable;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class OpposableGenerator extends
    BaseGenerator<Opposable, Opposable.OpposableBuilder> {

  public OpposableGenerator() {
    super(Opposable::toBuilder, Opposable.OpposableBuilder::build);
  }

  @Override
  public Opposable createDefault() {
    return Opposable.builder()
        .opposableType("ApplicationMeritsTask::Individual")
        .firstName("John")
        .lastName("Smith")
        .name("Acme Ltd")
        .build();
  }

  @Override
  public Opposable createRandom() {
    String[] types = {"ApplicationMeritsTask::Individual", "ApplicationMeritsTask::Organisation"};
    String selectedType = faker.options().option(types);

    return Opposable.builder()
        .opposableType(selectedType)
        .firstName(faker.name().firstName())
        .lastName(faker.name().lastName())
        .name(faker.company().name())
        .build();
  }
}
