package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullApplicant;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullApplicantGenerator
    extends BaseGenerator<FullApplicant, FullApplicant.FullApplicantBuilder> {

  private final FullApplicantAddressGenerator addressGenerator =
      new FullApplicantAddressGenerator();

  public FullApplicantGenerator() {
    super(FullApplicant::toBuilder, FullApplicant.FullApplicantBuilder::build);
  }

  private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
  }

  @Override
  public FullApplicant createDefault() {
    return FullApplicant.builder()
        .id(UUID.randomUUID().toString())
        .firstName(faker.name().firstName())
        .lastName(faker.name().lastName())
        .dateOfBirth(getRandomDate().toString())
        .createdAt(randomInstant())
        .updatedAt(randomInstant())
        .email(faker.internet().emailAddress())
        .nationalInsuranceNumber(faker.regexify("[A-Z]{2}[0-9]{6}[A-Z]"))
        .employed(null)
        .selfEmployed(faker.bool().bool())
        .armedForces(faker.bool().bool())
        .hasNationalInsuranceNumber(faker.bool().bool())
        .ageForMeansTestPurposes(faker.number().numberBetween(0, 17))
        .hasPartner(faker.bool().bool())
        .receivesStateBenefits(null)
        .partnerHasContraryInterest(null)
        .studentFinance(null)
        .studentFinanceAmount(null)
        .extraEmploymentInformation(null)
        .extraEmploymentInformationDetails(null)
        .lastNameAtBirth(faker.name().lastName())
        .changedLastName(faker.bool().bool())
        .sameCorrespondenceAndHomeAddress(faker.bool().bool())
        .noFixedResidence(false)
        .correspondenceAddressChoice(faker.options().option("home", "office"))
        .sharedBenefitWithPartner(null)
        .appliedPreviously(faker.bool().bool())
        .previousReference(null)
        .relationshipToChildren(faker.options().option("father", "mother", "guardian"))
        .addresses(List.of(addressGenerator.createDefault(), addressGenerator.createDefault()))
        .build();
  }
}
