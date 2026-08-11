package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.helpers.SpringContext;

public class ApplicationCreateRequestGenerator
    extends BaseGenerator<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {
  private final ApplicationContentGenerator applicationContentGenerator =
      new ApplicationContentGenerator();

  public ApplicationCreateRequestGenerator() {
    super(ApplicationCreateRequest::toBuilder, ApplicationCreateRequest.Builder::build);
  }

  @Override
  public ApplicationCreateRequest createDefault() {
    ObjectMapper mapper = SpringContext.getObjectMapper();
    return ApplicationCreateRequest.builder()
        .id(UUID.randomUUID())
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("REF7327")
        .applicationContent(
            mapper.convertValue(applicationContentGenerator.createDefault(), Map.class))
        .build();
  }
}
