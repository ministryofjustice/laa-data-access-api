package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import net.datafaker.Faker;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

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
    return new ApplicationSummaryResult() {
      @Override public UUID getId() { return id; }
      @Override public ApplicationStatus getStatus() { return status; }
      @Override public String getLaaReference() { return laaReference; }
      @Override public String getOfficeCode() { return null; }
      @Override public Instant getSubmittedAt() { return now; }
      @Override public Instant getModifiedAt() { return now; }
      @Override public Boolean getUsedDelegatedFunctions() { return null; }
      @Override public CategoryOfLaw getCategoryOfLaw() { return null; }
      @Override public MatterType getMatterType() { return null; }
      @Override public Boolean getIsAutoGranted() { return null; }
      @Override public Boolean getIsLead() { return isLead; }
      @Override public UUID getCaseworkerId() { return null; }
      @Override public String getClientFirstName() { return null; }
      @Override public String getClientLastName() { return null; }
      @Override public LocalDate getClientDateOfBirth() { return null; }
    };
  }
}