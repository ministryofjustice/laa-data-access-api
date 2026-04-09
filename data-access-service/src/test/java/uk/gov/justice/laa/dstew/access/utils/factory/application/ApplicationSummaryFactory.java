package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.time.Instant;
import java.util.UUID;
import net.datafaker.Faker;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

@Profile("unit-test")
@Component
public class ApplicationSummaryFactory {

  private final Faker faker = new Faker(new java.util.Random(12345L));

  public ApplicationSummaryResult createDefault() {
    return create(UUID.randomUUID(), "REF7327", ApplicationStatus.APPLICATION_IN_PROGRESS, false);
  }

  public ApplicationSummaryResult createDefault(UUID id, boolean isLead) {
    return create(id, "REF7327", ApplicationStatus.APPLICATION_IN_PROGRESS, isLead);
  }

  public ApplicationSummaryResult createRandom() {
    return create(UUID.randomUUID(), faker.bothify("REF####"), ApplicationStatus.APPLICATION_IN_PROGRESS, false);
  }

  private ApplicationSummaryResult create(UUID id, String laaReference, ApplicationStatus status, boolean isLead) {
    Instant now = Instant.now();
    return ApplicationSummaryResult.builder()
        .id(id)
        .status(status)
        .laaReference(laaReference)
        .submittedAt(now)
        .modifiedAt(now)
        .isLead(isLead)
        .build();
  }
}