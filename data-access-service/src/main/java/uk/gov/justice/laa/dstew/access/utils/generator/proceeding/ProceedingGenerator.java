package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ProceedingGenerator extends BaseGenerator<Proceeding, Proceeding.ProceedingBuilder> {
  public ProceedingGenerator() {
    super(Proceeding::toBuilder, Proceeding.ProceedingBuilder::build);
  }

  @Override
  public Proceeding createDefault() {
    return Proceeding.builder()
        .id(UUID.randomUUID())
        .categoryOfLaw("Family")
        .matterType("SPECIAL_CHILDREN_ACT")
        .leadProceeding(true)
        .usedDelegatedFunctions(true)
        .description("Proceeding description")
        .meaning("hearing")
        .usedDelegatedFunctionsOn(LocalDate.parse("2025-05-06"))
        .substantiveCostLimitation("23.45")
        .substantiveLevelOfServiceName("service")
        .scopeLimitations(
            List.of(
                Map.of(
                    "id", "100",
                    "code", "AB123D",
                    "meaning", "hearing")))
        .build();
  }

  @Override
  public Proceeding createRandom() {
    String[] categories = {"Family", "Civil", "Housing", "Immigration"};
    String[] matterTypes = {
      "SPECIAL_CHILDREN_ACT", "DOMESTIC_ABUSE", "PRIVATE_FAMILY", "PUBLIC_FAMILY"
    };
    String[] meanings = {"hearing", "application", "appeal", "review"};

    return Proceeding.builder()
        .id(UUID.randomUUID())
        .categoryOfLaw(faker.options().option(categories))
        .matterType(faker.options().option(matterTypes))
        .leadProceeding(faker.bool().bool())
        .usedDelegatedFunctions(faker.bool().bool())
        .description(faker.lorem().sentence())
        .meaning(faker.options().option(meanings))
        .usedDelegatedFunctionsOn(getRandomDate())
        .substantiveCostLimitation(
            String.format("%.2f", faker.number().randomDouble(2, 100, 100000)))
        .substantiveLevelOfServiceName(
            faker.options().option("Full Representation", "Legal Help", "Help at Court"))
        .scopeLimitations(
            List.of(
                Map.of(
                    "id", String.valueOf(faker.number().numberBetween(100, 999)),
                    "code", faker.regexify("[A-Z]{2}[0-9]{3}[A-Z]"),
                    "meaning", faker.options().option(meanings))))
        .build();
  }
}
