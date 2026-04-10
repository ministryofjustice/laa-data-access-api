package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.merit.MeritsDecisionDetailsFactory;

@Profile("unit-test")
@Component
public class MakeDecisionProceedingFactory
    extends BaseFactory<MakeDecisionProceedingRequest, MakeDecisionProceedingRequest.Builder> {

  @Autowired private MeritsDecisionDetailsFactory meritsDecisionDetailsFactory;

  public MakeDecisionProceedingFactory() {
    super(MakeDecisionProceedingRequest::toBuilder, MakeDecisionProceedingRequest.Builder::build);
  }

  @Override
  public MakeDecisionProceedingRequest createDefault() {
    return MakeDecisionProceedingRequest.builder()
        .proceedingId(UUID.randomUUID())
        .meritsDecision(meritsDecisionDetailsFactory.createDefault())
        .build();
  }
}
