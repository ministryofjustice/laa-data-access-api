package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationOffice;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ApplicationOfficeGenerator
    extends BaseGenerator<ApplicationOffice, ApplicationOffice.ApplicationOfficeBuilder> {

  public ApplicationOfficeGenerator() {
    super(ApplicationOffice::toBuilder, ApplicationOffice.ApplicationOfficeBuilder::build);
  }

  @Override
  public ApplicationOffice createDefault() {
    return ApplicationOffice.builder().code("officeCode").build();
  }
}
