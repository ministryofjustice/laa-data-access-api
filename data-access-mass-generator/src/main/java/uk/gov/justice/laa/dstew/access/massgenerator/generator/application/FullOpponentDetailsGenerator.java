package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullOpponentDetails;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullOpponentDetailsGenerator
    extends BaseGenerator<FullOpponentDetails, FullOpponentDetails.FullOpponentDetailsBuilder> {

  private final FullOpposableGenerator opposableGenerator = new FullOpposableGenerator();

  public FullOpponentDetailsGenerator() {
    super(FullOpponentDetails::toBuilder, FullOpponentDetails.FullOpponentDetailsBuilder::build);
  }

  private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
  }

  @Override
  public FullOpponentDetails createDefault() {
    return FullOpponentDetails.builder()
        .id(UUID.randomUUID().toString())
        .legalAidApplicationId(UUID.randomUUID().toString())
        .createdAt(randomInstant())
        .updatedAt(randomInstant())
        .ccmsOpponentId(null)
        .opposableType(
            faker
                .options()
                .option("ApplicationMeritsTask::Individual", "ApplicationMeritsTask::Organisation"))
        .opposableId(UUID.randomUUID().toString())
        .existsInCCMS(faker.bool().bool())
        .opposable(opposableGenerator.createDefault())
        .build();
  }
}
