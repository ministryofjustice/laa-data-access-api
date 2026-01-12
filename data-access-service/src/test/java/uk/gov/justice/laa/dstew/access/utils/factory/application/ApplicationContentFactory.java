package uk.gov.justice.laa.dstew.access.utils.factory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationContentDetails;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class ApplicationContentFactory
    extends BaseFactory<ApplicationContentDetails, ApplicationContentDetails.ApplicationContentDetailsBuilder> {

  @Autowired
  ObjectMapper objectMapper;
  @Autowired
  ProceedingDetailsFactory proceedingDetailsFactory;

  public ApplicationContentFactory() {
    super(ApplicationContentDetails::toBuilder, ApplicationContentDetails.ApplicationContentDetailsBuilder::build);
  }

  @Override
  public ApplicationContentDetails createDefault() {
    UUID applicationId = UUID.randomUUID();
    return ApplicationContentDetails.builder()
        .id(applicationId)
        .autoGrant(true)
        .submittedAt(Instant.now())
        .proceedings(List.of(proceedingDetailsFactory.createDefault()))
        .build();
  }


  public Map<String, Object> createDefaultAsMap() {
    return objectMapper.convertValue(this.createDefault(), Map.class);
  }

  public Map<String, Object> createDefaultAsMap(
      Consumer<ApplicationContentDetails.ApplicationContentDetailsBuilder> customiser) {
    return objectMapper.convertValue(this.createDefault(customiser), Map.class);
  }


}