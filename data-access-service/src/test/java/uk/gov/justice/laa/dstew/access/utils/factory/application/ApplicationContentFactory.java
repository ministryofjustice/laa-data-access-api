package uk.gov.justice.laa.dstew.access.utils.factory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;
import uk.gov.justice.laa.dstew.access.model.ApplicationContentDetails;
import uk.gov.justice.laa.dstew.access.model.ProceedingDetails;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class ApplicationContentFactory
    extends BaseFactory<ApplicationContentDetails, ApplicationContentDetails.ApplicationContentDetailsBuilder> {

  @Autowired
  ObjectMapper objectMapper;

  public ApplicationContentFactory() {
    super(ApplicationContentDetails::toBuilder, ApplicationContentDetails.ApplicationContentDetailsBuilder::build);
  }

  @Override
  public ApplicationContentDetails createDefault() {
    ProceedingDetails proceedingDetails = ProceedingDetails
        .builder()
        .id("123ABC")
        .categoryOfLaw(CategoryOfLaw.Family)
        .matterType(MatterType.SCA)
        .leadProceeding(true)
        .useDelegatedFunctions(true)
        .build();
    return ApplicationContentDetails.builder()
        .id("123")
        .autoGrant(true)
        .submittedAt(Instant.now())
        .proceedings(List.of(proceedingDetails))
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