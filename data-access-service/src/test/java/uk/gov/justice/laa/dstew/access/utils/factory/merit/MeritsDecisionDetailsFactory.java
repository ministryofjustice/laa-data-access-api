package uk.gov.justice.laa.dstew.access.utils.factory.merit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class MeritsDecisionDetailsFactory
    extends BaseFactory<MeritsDecisionDetailsRequest, MeritsDecisionDetailsRequest.Builder> {

  public MeritsDecisionDetailsFactory() {
    super(MeritsDecisionDetailsRequest::toBuilder, MeritsDecisionDetailsRequest.Builder::build);
  }

  @Override
  public MeritsDecisionDetailsRequest createDefault() {
    return MeritsDecisionDetailsRequest.builder().decision(MeritsDecisionStatus.REFUSED).build();
  }
}
