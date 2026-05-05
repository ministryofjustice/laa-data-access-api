package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplication;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/**
 * Generator for {@link LinkedApplication} domain records. Use this in use-case tests where lambdas
 * must type-check against domain builder methods.
 *
 * <p>For mapper/service tests that need {@code model.LinkedApplication}, use {@link
 * LinkedApplicationsGenerator}.
 */
public class DomainLinkedApplicationsGenerator
    extends BaseGenerator<LinkedApplication, LinkedApplication.LinkedApplicationBuilder> {

  public DomainLinkedApplicationsGenerator() {
    super(LinkedApplication::toBuilder, LinkedApplication.LinkedApplicationBuilder::build);
  }

  @Override
  public LinkedApplication createDefault() {
    return LinkedApplication.builder()
        .leadApplicationId(UUID.randomUUID())
        .associatedApplicationId(UUID.randomUUID())
        .build();
  }
}
