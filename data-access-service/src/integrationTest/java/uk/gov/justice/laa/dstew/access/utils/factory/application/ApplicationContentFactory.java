package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.ProceedingRequestFactoryImpl;

@Component
public class ApplicationContentFactory implements Factory<ApplicationContent, ApplicationContent.Builder> {


  private ProceedingRequestFactoryImpl proceedingRequestFactory = new ProceedingRequestFactoryImpl();


  @Override
  public ApplicationContent create() {
    UUID applicationId = UUID.randomUUID();

    // Build base content with required fields
    ApplicationContent content = ApplicationContent.builder()
        .id(applicationId)
        .proceedings(List.of(proceedingRequestFactory.create()))
        .submittedAt("2024-01-01T12:00:00Z")
        .build();

    // Force additional properties via the generated API so getAdditionalProperties() is non-null
    content.putAdditionalProperty("test", "additionalApplicationProperty");

    return content;
  }

  @Override
  public ApplicationContent create(Consumer<ApplicationContent.Builder> customiser) {
    ApplicationContent.Builder builder = ApplicationContent.builder();
    customiser.accept(builder);
    return builder.build();
  }
}