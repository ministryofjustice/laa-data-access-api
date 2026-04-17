package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullScopeLimitation;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullScopeLimitationGenerator
    extends BaseGenerator<FullScopeLimitation, FullScopeLimitation.FullScopeLimitationBuilder> {

  public FullScopeLimitationGenerator() {
    super(FullScopeLimitation::toBuilder, FullScopeLimitation.FullScopeLimitationBuilder::build);
  }

  private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
  }

  @Override
  public FullScopeLimitation createDefault() {
    return FullScopeLimitation.builder()
        .id(UUID.randomUUID().toString())
        .scopeType(faker.options().option("substantive", "emergency"))
        .code(faker.regexify("[A-Z]{2}[0-9]{3}"))
        .meaning(faker.options().option("Final hearing", "All steps", "Hearing"))
        .description(faker.lorem().sentence())
        .hearingDate(null)
        .limitationNote(null)
        .createdAt(randomInstant())
        .updatedAt(randomInstant())
        .build();
  }
}
