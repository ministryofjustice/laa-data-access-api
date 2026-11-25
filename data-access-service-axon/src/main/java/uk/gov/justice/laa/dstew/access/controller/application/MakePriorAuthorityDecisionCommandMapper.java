package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.ApportionmentInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;
import uk.gov.justice.laa.dstew.access.model.ApportionmentMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionTimeRequested;
import uk.gov.justice.laa.dstew.access.model.DisbursementMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ExpertMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedUserId;

/** Maps the make-decision HTTP contract to a prior-authority decision command. */
@Component
public class MakePriorAuthorityDecisionCommandMapper {

  private final AuthenticatedUserId authenticatedUserId;

  public MakePriorAuthorityDecisionCommandMapper(AuthenticatedUserId authenticatedUserId) {
    this.authenticatedUserId = authenticatedUserId;
  }

  /** Maps a request for the supplied PriorAuthority identifier. */
  public MakePriorAuthorityDecisionCommand toCommand(
      UUID priorAuthorityId, MakePriorAuthorityDecisionRequest request) {
    return new MakePriorAuthorityDecisionCommand(
        priorAuthorityId,
        authenticatedUserId.get(),
        request.getPriorAuthorityVersion(),
        request.getDecision().name(),
        decisionJustification(request),
        request.getAmountGranted(),
        expertFeeInformation(request.getExpert()),
        disbursementInformation(request.getDisbursement()),
        apportionmentInformation(request.getApportionment()),
        request.getDateGranted().toInstant(),
        Instant.now());
  }

  private ApportionmentInformation apportionmentInformation(
      ApportionmentMakePriorAuthorityDecisionRequest value) {
    return value == null
        ? null
        : ApportionmentInformation.builder()
            .newClientShareAmount(value.getNewClientShareAmount())
            .build();
  }

  private DisbursementInformation disbursementInformation(
      DisbursementMakePriorAuthorityDecisionRequest value) {
    return value == null
        ? null
        : DisbursementInformation.builder().newAmount(value.getNewAmount()).build();
  }

  private ExpertFeeInformation expertFeeInformation(ExpertMakePriorAuthorityDecisionRequest value) {
    return value == null
        ? null
        : ExpertFeeInformation.builder()
            .newFixedRateAmount(value.getNewFixedRateAmount())
            .newHourlyRateAmount(value.getNewHourlyRateAmount())
            .grantedTime(toTimeRequested(value.getGrantedTime()))
            .build();
  }

  private TimeRequested toTimeRequested(DecisionTimeRequested value) {
    return value == null ? null : new TimeRequested(value.getHours(), value.getMinutes());
  }

  private String decisionJustification(MakePriorAuthorityDecisionRequest request) {
    String value = request.getDecisionJustification();
    return value == null ? null : value.trim();
  }
}
