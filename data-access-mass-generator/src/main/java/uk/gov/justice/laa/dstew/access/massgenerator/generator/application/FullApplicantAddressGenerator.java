package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullApplicantAddress;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullApplicantAddressGenerator
    extends BaseGenerator<FullApplicantAddress, FullApplicantAddress.FullApplicantAddressBuilder> {

  public FullApplicantAddressGenerator() {
    super(FullApplicantAddress::toBuilder, FullApplicantAddress.FullApplicantAddressBuilder::build);
  }

  private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
  }

  @Override
  public FullApplicantAddress createDefault() {
    return FullApplicantAddress.builder()
        .id(UUID.randomUUID().toString())
        .addressLineOne(faker.address().streetAddress())
        .addressLineTwo(faker.address().secondaryAddress())
        .addressLineThree(null)
        .city(faker.address().city())
        .county(faker.address().state())
        .postcode(faker.regexify("[A-Z]{2}[0-9][A-Z] [0-9][A-Z]{2}"))
        .applicantId(UUID.randomUUID().toString())
        .createdAt(randomInstant())
        .updatedAt(randomInstant())
        .organisation(null)
        .lookupUsed(faker.bool().bool())
        .lookupId(null)
        .buildingNumberName(faker.address().buildingNumber())
        .location(faker.options().option("home", "correspondence"))
        .countryCode("GBR")
        .countryName("United Kingdom")
        .careOf(null)
        .careOfFirstName(null)
        .careOfLastName(null)
        .careOfOrganisationName(null)
        .build();
  }
}
