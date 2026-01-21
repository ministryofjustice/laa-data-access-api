package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualEntityFactory;

@Profile("unit-test")
@Component
public class ApplicationEntityFactory extends BaseFactory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> {

  @Autowired
  private IndividualEntityFactory individualEntityFactory;
  @Autowired
  private ApplicationContentFactory applicationContentFactory;

  public ApplicationEntityFactory() {
    super(ApplicationEntity::toBuilder, ApplicationEntity.ApplicationEntityBuilder::build);
  }

  @Override
  public ApplicationEntity createDefault() {
    return ApplicationEntity.builder()
        .schemaVersion(1)
        .createdAt(InstantSource.system().instant())
        .id(UUID.randomUUID())
        .applyApplicationId(UUID.randomUUID())
        .status(ApplicationStatus.IN_PROGRESS)
        .modifiedAt(InstantSource.system().instant())
        .submittedAt(InstantSource.system().instant())
        .laaReference("REF7327")
        .individuals(Set.of(
            individualEntityFactory.createDefault()
        ))
        .applicationContent(applicationContentFactory.createDefaultAsMapWithApplicationContent())
        .useDelegatedFunctions(false)
        .isAutoGranted(true)
        .build();
  }

  @Override
  public ApplicationEntity createRandom() {
    return createDefault().toBuilder()
        .laaReference(faker.bothify("REF####"))
        .individuals(Set.of(
            individualEntityFactory.createRandom()
        ))
        .applicationContent(Map.of(
            "test", faker.text().text(50)
        ))
        .build();
  }
}