package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ApportionmentInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.model.ApportionmentMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.DisbursementMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ExpertMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;

/** Maps the make-decision HTTP contract to a prior-authority decision command. */
@Component
public class MakePriorAuthorityDecisionCommandMapper {

  private final ObjectMapper objectMapper;

  public MakePriorAuthorityDecisionCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps a request for the supplied PriorAuthority identifier. */
  public MakePriorAuthorityDecisionCommand toCommand(
      UUID priorAuthorityId, MakePriorAuthorityDecisionRequest request) {
    return new MakePriorAuthorityDecisionCommand(
        priorAuthorityId,
        request.getPriorAuthorityVersion(),
        request.getDecision().name(),
        decisionJustification(request),
        request.getAmountGranted(),
        expertFeeInformation(request.getExpert()),
        disbursementInformation(request.getDisbursement()),
        apportionmentInformation(request.getApportionment()),
        request.getDateGranted().toInstant(),
        serialise(request),
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
            .build();
  }

  private String decisionJustification(MakePriorAuthorityDecisionRequest request) {
    String value = request.getDecisionJustification();
    return value == null ? null : value.trim();
  }

  private String serialise(MakePriorAuthorityDecisionRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "Unable to serialise MakePriorAuthorityDecisionRequest", exception);
    }
  }
}
