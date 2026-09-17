package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.model.ApportionmentMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DisbursementMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.ExpertMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedUserId;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

class MakePriorAuthorityDecisionCommandMapperTest {

  private final AuthenticatedUserId authenticatedUserId = mock(AuthenticatedUserId.class);

  private final MakePriorAuthorityDecisionCommandMapper mapper =
      new MakePriorAuthorityDecisionCommandMapper(
          JsonMapper.builder().build(), authenticatedUserId);

  @Test
  void givenRequestWithEventDescription_whenMapped_thenUsesEventDescriptionAsJustification() {
    UUID submissionId = UUID.randomUUID();
    OffsetDateTime dateGranted = OffsetDateTime.parse("2026-09-08T12:30:00Z");
    when(authenticatedUserId.get()).thenReturn(TestJwtDecoderConfig.CASEWORKER_ID);
    MakePriorAuthorityDecisionRequest request =
        MakePriorAuthorityDecisionRequest.builder()
            .priorAuthorityVersion(7L)
            .decision(DecisionStatus.GRANTED)
            .decisionJustification("Decision recorded")
            .expert(
                ExpertMakePriorAuthorityDecisionRequest.builder()
                    .newFixedRateAmount(450.25)
                    .newHourlyRateAmount(125.75)
                    .build())
            .disbursement(
                DisbursementMakePriorAuthorityDecisionRequest.builder().newAmount(300.00).build())
            .apportionment(
                ApportionmentMakePriorAuthorityDecisionRequest.builder()
                    .newClientShareAmount(200.00)
                    .build())
            .amountGranted(1200.50)
            .dateGranted(dateGranted)
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .build();

    MakePriorAuthorityDecisionCommand command = mapper.toCommand(submissionId, request);

    assertThat(command.submissionId()).isEqualTo(submissionId);
    assertThat(command.caseworkerId()).isEqualTo(TestJwtDecoderConfig.CASEWORKER_ID);
    assertThat(command.expectedPriorAuthorityVersion()).isEqualTo(7L);
    assertThat(command.overallDecision()).isEqualTo("GRANTED");
    assertThat(command.decisionJustification()).isEqualTo("Decision recorded");
    assertThat(command.amountGranted()).isEqualTo(1200.50);
    assertThat(command.expertFee()).isNotNull();
    assertThat(command.expertFee().getNewFixedRateAmount()).isEqualTo(450.25);
    assertThat(command.expertFee().getNewHourlyRateAmount()).isEqualTo(125.75);
    assertThat(command.disbursementInformation()).isNotNull();
    assertThat(command.disbursementInformation().getNewAmount()).isEqualTo(300.00);
    assertThat(command.apportionmentInformation()).isNotNull();
    assertThat(command.apportionmentInformation().getNewClientShareAmount()).isEqualTo(200.00);
    assertThat(command.dateGranted()).isEqualTo(dateGranted.toInstant());
    assertThat(command.serialisedRequest()).contains("\"decision\":\"GRANTED\"");
    assertThat(command.occurredAt()).isNotNull();
  }

  @Test
  void givenRequestWithExpertFeeDetails_whenMapped_thenStoresExpertFeeInformation() {
    UUID submissionId = UUID.randomUUID();
    when(authenticatedUserId.get()).thenReturn(TestJwtDecoderConfig.CASEWORKER_ID);
    MakePriorAuthorityDecisionRequest request =
        MakePriorAuthorityDecisionRequest.builder()
            .priorAuthorityVersion(7L)
            .decision(DecisionStatus.GRANTED)
            .decisionJustification("Decision recorded")
            .amountGranted(1200.50)
            .dateGranted(OffsetDateTime.parse("2026-09-08T12:30:00Z"))
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .expert(
                ExpertMakePriorAuthorityDecisionRequest.builder()
                    .newHourlyRateAmount(125.75)
                    .build())
            .disbursement(DisbursementMakePriorAuthorityDecisionRequest.builder().build())
            .apportionment(null)
            .build();

    MakePriorAuthorityDecisionCommand command = mapper.toCommand(submissionId, request);

    assertThat(command.submissionId()).isEqualTo(submissionId);
    assertThat(command.expertFee()).isNotNull();
    assertThat(command.expertFee().getNewFixedRateAmount()).isNull();
    assertThat(command.expertFee().getNewHourlyRateAmount()).isEqualTo(125.75);
    assertThat(command.disbursementInformation()).isNotNull();
    assertThat(command.disbursementInformation().getNewAmount()).isNull();
    assertThat(command.apportionmentInformation()).isNull();
    assertThat(command.serialisedRequest()).contains("\"newFixedRateAmount\":null");
    assertThat(command.serialisedRequest()).contains("\"newHourlyRateAmount\":125.75");
  }

  @Test
  void givenDecisionJustificationWithWhitespace_whenMapped_thenStoresTrimmedJustification() {
    when(authenticatedUserId.get()).thenReturn(TestJwtDecoderConfig.CASEWORKER_ID);
    MakePriorAuthorityDecisionRequest request =
        MakePriorAuthorityDecisionRequest.builder()
            .priorAuthorityVersion(8L)
            .decision(DecisionStatus.REFUSED)
            .decisionJustification("  Refusal reason  ")
            .amountGranted(0.0)
            .expert(
                ExpertMakePriorAuthorityDecisionRequest.builder().newFixedRateAmount(0.0).build())
            .disbursement(DisbursementMakePriorAuthorityDecisionRequest.builder().build())
            .apportionment(ApportionmentMakePriorAuthorityDecisionRequest.builder().build())
            .dateGranted(OffsetDateTime.parse("2026-09-08T12:40:00Z"))
            .eventHistory(EventHistoryRequest.builder().eventDescription("ignored").build())
            .build();

    MakePriorAuthorityDecisionCommand command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.expectedPriorAuthorityVersion()).isEqualTo(8L);
    assertThat(command.overallDecision()).isEqualTo("REFUSED");
    assertThat(command.expertFee().getNewFixedRateAmount()).isEqualTo(0.0);
    assertThat(command.expertFee().getNewHourlyRateAmount()).isNull();
    assertThat(command.disbursementInformation().getNewAmount()).isNull();
    assertThat(command.apportionmentInformation().getNewClientShareAmount()).isNull();
    assertThat(command.decisionJustification()).isEqualTo("Refusal reason");
  }

  @Test
  void givenNullDecisionJustification_whenMapped_thenStoresNullJustification() {
    when(authenticatedUserId.get()).thenReturn(TestJwtDecoderConfig.CASEWORKER_ID);
    MakePriorAuthorityDecisionRequest request =
        MakePriorAuthorityDecisionRequest.builder()
            .priorAuthorityVersion(10L)
            .decision(DecisionStatus.GRANTED)
            .decisionJustification(null)
            .amountGranted(42.0)
            .dateGranted(OffsetDateTime.parse("2026-09-08T13:00:00Z"))
            .eventHistory(EventHistoryRequest.builder().eventDescription("decision").build())
            .build();

    MakePriorAuthorityDecisionCommand command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.expectedPriorAuthorityVersion()).isEqualTo(10L);
    assertThat(command.decisionJustification()).isNull();
    assertThat(command.expertFee()).isNull();
    assertThat(command.disbursementInformation()).isNull();
  }

  @Test
  void givenSerializationFailure_whenMapped_thenWrapsInIllegalStateException() throws Exception {
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    AuthenticatedUserId authenticatedUserId = mock(AuthenticatedUserId.class);
    MakePriorAuthorityDecisionCommandMapper failingMapper =
        new MakePriorAuthorityDecisionCommandMapper(objectMapper, authenticatedUserId);
    MakePriorAuthorityDecisionRequest request =
        MakePriorAuthorityDecisionRequest.builder()
            .priorAuthorityVersion(9L)
            .decision(DecisionStatus.GRANTED)
            .decisionJustification("Decision recorded")
            .amountGranted(900.0)
            .dateGranted(OffsetDateTime.parse("2026-09-08T12:50:00Z"))
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .build();
    when(authenticatedUserId.get()).thenReturn(TestJwtDecoderConfig.CASEWORKER_ID);
    when(objectMapper.writeValueAsString(request)).thenThrow(new JacksonException("boom") {});

    assertThatThrownBy(() -> failingMapper.toCommand(UUID.randomUUID(), request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to serialise MakePriorAuthorityDecisionRequest")
        .hasCauseInstanceOf(JacksonException.class);
  }
}
