package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualFactory;

import java.util.List;

@Profile("unit-test")
@Component
public class ApplicationCreateRequestFactory extends BaseFactory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {

    @Autowired
    private IndividualFactory individualFactory;

  @Autowired
  private RequestApplicationContentFactory requestApplicationContentFactory;

  public ApplicationCreateRequestFactory() {
        super(ApplicationCreateRequest::toBuilder, ApplicationCreateRequest.Builder::build);
    }

    @Override
    public ApplicationCreateRequest createDefault() {
        return ApplicationCreateRequest.builder()
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .laaReference("REF7327")
                .individuals(List.of(individualFactory.createDefault()))
                .applicationContent(MapperUtil.getObjectMapper().convertValue(requestApplicationContentFactory.createDefault(), Map.class))
                .build();
    }
}