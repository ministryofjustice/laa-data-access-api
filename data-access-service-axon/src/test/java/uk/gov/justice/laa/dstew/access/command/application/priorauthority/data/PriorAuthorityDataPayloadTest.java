package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.ApportionmentInformation;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;

class PriorAuthorityDataPayloadTest {

  private static final Instant SUBMITTED_AT = Instant.parse("2026-09-16T10:00:00Z");
  private static final Instant GRANTED_AT = Instant.parse("2026-09-16T11:00:00Z");

  @Test
  void givenUndecidedPayload_whenReadingDecisionFields_thenAllDerivedFieldsAreNull() {
    PriorAuthorityDataPayload payload = undecidedPayload();

    assertThat(payload.decisionDetails()).isNull();
    assertThat(payload.decision()).isNull();
    assertThat(payload.decisionJustification()).isNull();
    assertThat(payload.amountGranted()).isNull();
    assertThat(payload.dateGranted()).isNull();
    assertThat(payload.expert()).isNull();
    assertThat(payload.disbursement()).isNull();
    assertThat(payload.apportionment()).isNull();
    assertThat(payload.decisionSerialisedRequest()).isNull();
  }

  @Test
  void givenUndecidedPayload_whenApplyingDecision_thenReturnsNewDecidedVersion() {
    PriorAuthorityDataPayload original = undecidedPayload();
    PriorAuthorityDataPayload.DecisionDetails decisionDetails = decisionDetails();

    PriorAuthorityDataPayload decided = original.withDecision(decisionDetails);

    assertThat(decided).isNotSameAs(original);
    assertThat(decided.priorAuthorityId()).isEqualTo(original.priorAuthorityId());
    assertThat(decided.applicationId()).isEqualTo(original.applicationId());
    assertThat(decided.content()).isEqualTo(original.content());
    assertThat(decided.serialisedRequest()).isEqualTo(original.serialisedRequest());
    assertThat(decided.submittedAt()).isEqualTo(original.submittedAt());

    assertThat(decided.decision()).isEqualTo("GRANTED");
    assertThat(decided.decisionJustification()).isEqualTo("Reasoned decision");
    assertThat(decided.amountGranted()).isEqualByComparingTo(BigDecimal.valueOf(123.45));
    assertThat(decided.dateGranted()).isEqualTo(GRANTED_AT);
    assertThat(decided.expert()).isEqualTo(decisionDetails.expert());
    assertThat(decided.disbursement()).isEqualTo(decisionDetails.disbursement());
    assertThat(decided.apportionment()).isEqualTo(decisionDetails.apportionment());
    assertThat(decided.decisionSerialisedRequest()).isEqualTo("{\"decision\":\"GRANTED\"}");
  }

  private static PriorAuthorityDataPayload undecidedPayload() {
    return new PriorAuthorityDataPayload(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Need expert", null, null, null),
        "{}",
        SUBMITTED_AT);
  }

  private static PriorAuthorityDataPayload.DecisionDetails decisionDetails() {
    return new PriorAuthorityDataPayload.DecisionDetails(
        "GRANTED",
        "Reasoned decision",
        BigDecimal.valueOf(123.45),
        GRANTED_AT,
        ExpertFeeInformation.builder()
            .newFixedRateAmount(BigDecimal.valueOf(250.0))
            .newHourlyRateAmount(BigDecimal.valueOf(100.0))
            .build(),
        DisbursementInformation.builder().newAmount(BigDecimal.valueOf(45.0)).build(),
        ApportionmentInformation.builder().newClientShareAmount(BigDecimal.valueOf(15.0)).build(),
        "{\"decision\":\"GRANTED\"}");
  }
}
