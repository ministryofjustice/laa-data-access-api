package uk.gov.justice.laa.dstew.access.utils.generator.getallindividuals;

import java.util.List;
import java.util.Map;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Test data generator for {@link ApplicationClientDetailsDomain}. */
public class ApplicationClientDetailsDomainGenerator
    extends BaseGenerator<
        ApplicationClientDetailsDomain,
        ApplicationClientDetailsDomain.ApplicationClientDetailsDomainBuilder> {

  public ApplicationClientDetailsDomainGenerator() {
    super(
        ApplicationClientDetailsDomain::toBuilder,
        ApplicationClientDetailsDomain.ApplicationClientDetailsDomainBuilder::build);
  }

  @Override
  public ApplicationClientDetailsDomain createDefault() {
    return ApplicationClientDetailsDomain.builder()
        .lastNameAtBirth("Alberts")
        .previousApplicationId("ZZ999Z")
        .relationshipToInvolvedChildren("Mother")
        .correspondenceAddressType("Home")
        .appliedPreviously(true)
        .correspondenceAddress(
            List.of(Map.of("line1", "1 Test Street"), Map.of("line1", "City Centre")))
        .build();
  }
}
