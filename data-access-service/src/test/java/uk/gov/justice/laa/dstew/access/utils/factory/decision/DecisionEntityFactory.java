package uk.gov.justice.laa.dstew.access.utils.factory.decision;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class DecisionEntityFactory
    extends BaseFactory<DecisionEntity, DecisionEntity.DecisionEntityBuilder> {

  public DecisionEntityFactory() {
    super(DecisionEntity::toBuilder, DecisionEntity.DecisionEntityBuilder::build);
  }

  @Override
  public DecisionEntity createDefault() {
    return DecisionEntity.builder().id(UUID.randomUUID()).build();
  }
}
