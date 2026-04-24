package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationOffice;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullOfficeGenerator
    extends BaseGenerator<ApplicationOffice, ApplicationOffice.ApplicationOfficeBuilder> {

  private final FullOfficeScheduleGenerator scheduleGenerator = new FullOfficeScheduleGenerator();

  public FullOfficeGenerator() {
    super(ApplicationOffice::toBuilder, ApplicationOffice.ApplicationOfficeBuilder::build);
  }

  private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
  }

  @Override
  public ApplicationOffice createDefault() {
    return ApplicationOffice.builder()
        .code(faker.regexify("[0-9][A-Z][0-9]{3}[A-Z]"))
        .build()
        .putAdditionalProperty("id", UUID.randomUUID().toString())
        .putAdditionalProperty("createdAt", randomInstant())
        .putAdditionalProperty("updatedAt", randomInstant())
        .putAdditionalProperty("ccmsId", faker.numerify("######"))
        .putAdditionalProperty("firmId", UUID.randomUUID().toString())
        .putAdditionalProperty("schedules", List.of(scheduleGenerator.createDefault()));
  }
}
