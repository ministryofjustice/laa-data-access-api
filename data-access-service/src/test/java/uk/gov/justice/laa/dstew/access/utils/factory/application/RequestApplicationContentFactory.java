package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.RequestApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class RequestApplicationContentFactory
    extends BaseFactory<RequestApplicationContent, RequestApplicationContent.Builder> {

  ApplicationContentFactory applicationContentFactory = new ApplicationContentFactory();


  public RequestApplicationContentFactory() {
    super(RequestApplicationContent::toBuilder, RequestApplicationContent.Builder::build);
  }

  @Override
  public RequestApplicationContent createDefault() {
    UUID applicationId = UUID.randomUUID();
    RequestApplicationContent requestApplicationContent = RequestApplicationContent.builder()
        .applicationContent(applicationContentFactory.createDefault())
        .applicationReference("LXB-111-111")
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .build();
    requestApplicationContent.putAdditionalProperty("applicationId", applicationId.toString());
    return requestApplicationContent;


  }

  public Map<String, Object> createDefaultAsMapWithApplicationContent() {
    return MapperUtil.getObjectMapper().convertValue(this.createDefault(), Map.class);
  }


}