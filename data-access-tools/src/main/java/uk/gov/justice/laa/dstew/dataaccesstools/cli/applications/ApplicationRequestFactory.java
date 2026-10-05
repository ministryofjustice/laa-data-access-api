    this(ThreadLocalRandom.current().nextLong());
                randomOfficeCode(),
package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.datafaker.Faker;

public final class ApplicationRequestFactory {

  private static final String REFERENCE_PATTERN =
      "L-[0-9ABCDEFHJKLMNPRTUVWXY]{3}-[0-9ABCDEFHJKLMNPRTUVWXY]{3}";

  // matterType/categoryOfLaw have only one valid domain value today, so they stay fixed.
  private static final List<ClientInvolvement> CLIENT_INVOLVEMENTS =
      List.of(
          new ClientInvolvement("Respondent", "A"),
          new ClientInvolvement("Applicant", "B"),
          new ClientInvolvement("Third party", "C"));
  private static final List<LevelOfService> LEVELS_OF_SERVICE =
      List.of(
          new LevelOfService(2, "Legal Help"),
          new LevelOfService(3, "Full Representation"),
          new LevelOfService(4, "Litigation Friend"));
  private static final List<ScopeLimitation> SCOPE_LIMITATIONS =
      List.of(
          new ScopeLimitation(
              "Final hearing", "Limited to all steps up to and including the final hearing"),
          new ScopeLimitation("All steps", "Limited to all steps in the proceedings"),
          new ScopeLimitation("Emergency hearing", "Limited to the emergency hearing only"));

  private final Faker faker;
  private final String officeCode;
  private final Set<String> generatedReferences = new HashSet<>();

  public ApplicationRequestFactory() {
    this(ThreadLocalRandom.current().nextLong(), null);
  }

  public ApplicationRequestFactory(long seed) {
    this(seed, null);
  }

  public ApplicationRequestFactory(long seed, String officeCode) {
    this.faker = new Faker(new Random(seed));
    if (officeCode != null && !officeCode.matches(OfficeCodeConverter.PATTERN)) {
      throw new IllegalArgumentException("officeCode must match " + OfficeCodeConverter.PATTERN);
    }
    this.officeCode = officeCode;
  }

  public ApplicationData create() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    String reference = generateReference();
    String timestamp = Instant.now().toString();
    ClientInvolvement clientInvolvement = faker.options().nextElement(CLIENT_INVOLVEMENTS);
    LevelOfService levelOfService = faker.options().nextElement(LEVELS_OF_SERVICE);
    ScopeLimitation scopeLimitation = faker.options().nextElement(SCOPE_LIMITATIONS);
    String request =
        """
        {"id":"%s","status":"APPLICATION_SUBMITTED","laaReference":"%s","applicationContent":{
          "createdAt":"%s","submittedAt":"%s",
          "provider":{"officeCode":"%s","contactEmail":"%s"},
          "client":{"firstName":"%s","lastName":"%s","dateOfBirth":"%s","appliedPreviously":%s,
            "addresses":[{"location":"home","addressLineOne":"%s","city":"%s","postcode":"%s","countryCode":"GBR","countryName":"United Kingdom"}]},
          "proceedings":[{"id":"%s","leadProceeding":true,"code":"SE003","meaning":"Care order","description":"Care order","matterType":"SPECIAL_CHILDREN_ACT","matterTypeCode":"KPBLW","categoryOfLaw":"Family","categoryOfLawCode":"MAT","clientInvolvementType":"%s","clientInvolvementTypeCode":"%s","usedDelegatedFunctions":false,"delegatedFunctionsCostLimitation":"0","substantiveCostLimitation":"%d","substantiveLevelOfService":%d,"substantiveLevelOfServiceName":"%s","emergencyLevelOfService":%d,"emergencyLevelOfServiceName":"%s","scopeLimitations":[{"id":"%s","type":"SUBSTANTIVE","code":"%s","meaning":"%s","description":"%s"}]}]
        }}
        """
            .formatted(
                applicationId,
                reference,
                timestamp,
                timestamp,
                officeCode == null ? randomOfficeCode() : officeCode,
                faker.internet().emailAddress(),
                faker.name().firstName(),
                faker.name().lastName(),
                randomDateOfBirth(),
                faker.bool().bool(),
                faker.address().streetAddress(),
                faker.address().city(),
                randomPostcode(),
                proceedingId,
                clientInvolvement.type(),
                clientInvolvement.code(),
                faker.number().numberBetween(1000, 50000),
                levelOfService.level(),
                levelOfService.name(),
                levelOfService.level(),
                levelOfService.name(),
                UUID.randomUUID(),
                randomScopeLimitationCode(),
                scopeLimitation.meaning(),
                scopeLimitation.description());
    return new ApplicationData(applicationId, proceedingId, reference, request);
  }

  private String generateReference() {
    String reference;
    do {
      reference = faker.regexify(REFERENCE_PATTERN);
    } while (!generatedReferences.add(reference));
    return reference;
  }

  private String randomOfficeCode() {
    return faker.regexify("[0-9][A-Z][0-9]{3}[A-Z]");
  }

  private String randomPostcode() {
    return faker.regexify("[A-Z]{2}[0-9][A-Z] [0-9][A-Z]{2}");
  }

  private String randomScopeLimitationCode() {
    return faker.regexify("FM[0-9]{3}");
  }

  private LocalDate randomDateOfBirth() {
    int age = faker.number().numberBetween(18, 90);
    return LocalDate.now().minusYears(age).minusDays(faker.number().numberBetween(0, 365));
  }

  public record ApplicationData(
      UUID applicationId, UUID proceedingId, String laaReference, String request) {}

  private record ClientInvolvement(String type, String code) {}

  private record LevelOfService(int level, String name) {}

  private record ScopeLimitation(String meaning, String description) {}
}
