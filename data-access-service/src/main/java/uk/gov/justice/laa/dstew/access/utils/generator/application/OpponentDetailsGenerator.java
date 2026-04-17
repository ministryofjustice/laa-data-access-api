package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.OpponentDetails;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class OpponentDetailsGenerator
    extends BaseGenerator<OpponentDetails, OpponentDetails.OpponentDetailsBuilder> {

  private final OpposableGenerator opposableGenerator = new OpposableGenerator();

  public OpponentDetailsGenerator() {
    super(OpponentDetails::toBuilder, OpponentDetails.OpponentDetailsBuilder::build);
  }

  @Override
  public OpponentDetails createDefault() {
    return OpponentDetails.builder().opposable(opposableGenerator.createDefault()).build();
  }

  @Override
  public OpponentDetails createRandom() {
    return OpponentDetails.builder().opposable(opposableGenerator.createRandom()).build();
  }
}
