package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.ProceedingFactory;

@Profile("unit-test")
@Component
public class ApplicationContentFactory
    extends BaseFactory<ApplicationContent, ApplicationContent.ApplicationContentBuilder> {

  ProceedingFactory proceedingFactory = new ProceedingFactory();

  public ApplicationContentFactory() {
    super(ApplicationContent::toBuilder, ApplicationContent.ApplicationContentBuilder::build);
  }

  @Override
  public ApplicationContent createDefault() {
    UUID applicationId = UUID.randomUUID();

    // Build base content with required fields
    ApplicationContent content = ApplicationContent.builder()
        .id(applicationId)
        .proceedings(List.of(proceedingFactory.createDefault()))
        .submittedAt(java.time.Instant.now().toString())
        .build();

    // Force additional properties via the generated API so getAdditionalProperties() is non-null
    content.putAdditionalApplicationContent("applicationId", applicationId.toString());

    return content;
  }


}