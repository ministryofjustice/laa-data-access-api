package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.model.Apportionment;
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CounselDetails;
import uk.gov.justice.laa.dstew.access.model.CounselType;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.ExpertCosts;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.TimeRequested;

class SavePriorAuthorityDraftCommandMapperTest {

  private final SavePriorAuthorityDraftCommandMapper mapper =
      new SavePriorAuthorityDraftCommandMapper(JsonMapper.builder().build());

  @Test
  void givenExpertRequest_whenCreateMapped_thenMapsAllExpertFields() {
    UUID applicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftCommand command =
        mapper.toCreateCommand(expertRequest().applicationId(applicationId));
    var expertDetails = command.content().expertDetails();
    var expertCosts = expertDetails.expertCosts();
    var timeRequested = expertCosts.timeRequested();
    var apportionment = expertCosts.apportionment();

    assertThat(command.priorAuthorityId()).isNotNull();
    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.content().priorAuthorityType())
        .isEqualTo(
            uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.EXPERT);
    assertThat(command.content().justification()).isEqualTo("Need expert assessment");
    assertThat(expertDetails).isNotNull();
    assertThat(expertDetails.expertType()).isEqualTo("Pathologist");
    assertThat(expertDetails.expertFullName()).isEqualTo("Casey Expert");
    assertThat(expertDetails.expertPostcode()).isEqualTo("AB1 2CD");
    assertThat(expertCosts).isNotNull();
    assertThat(expertCosts.billingType())
        .isEqualTo(uk.gov.justice.laa.dstew.access.content.priorauthority.BillingType.HOURLY);
    assertThat(expertCosts.hourlyRate()).isEqualByComparingTo(BigDecimal.valueOf(300.0));
    assertThat(timeRequested).isNotNull();
    assertThat(timeRequested.hours()).isEqualTo(2);
    assertThat(timeRequested.minutes()).isEqualTo(30);
    assertThat(expertCosts.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(900.0));
    assertThat(expertCosts.costsSharedWithOtherParties()).isTrue();
    assertThat(apportionment).isNotNull();
    assertThat(apportionment.partiesSharingCosts()).isEqualTo(2);
    assertThat(apportionment.clientShareAmount()).isEqualByComparingTo(BigDecimal.valueOf(450.0));
  }

  @Test
  void givenCounselRequest_whenCreateMapped_thenMapsCounselDetails() {
    CreatePriorAuthorityDraftCommand command = mapper.toCreateCommand(counselRequest());

    assertThat(command.content().priorAuthorityType())
        .isEqualTo(
            uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.COUNSEL);
    assertThat(command.content().counselDetails()).isNotNull();
    assertThat(command.content().counselDetails().counselType())
        .isEqualTo(
            uk.gov.justice.laa.dstew.access.content.priorauthority.CounselType.KINGS_COUNSEL_ALONE);
  }

  @Test
  void givenDisbursementRequest_whenCreateMapped_thenMapsDisbursementDetails() {
    CreatePriorAuthorityDraftCommand command = mapper.toCreateCommand(disbursementRequest());

    assertThat(command.content().priorAuthorityType())
        .isEqualTo(
            uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.DISBURSEMENT);
    assertThat(command.content().disbursementDetails()).isNotNull();
    assertThat(command.content().disbursementDetails().disbursementPurpose())
        .isEqualTo("Interpreter");
    assertThat(command.content().disbursementDetails().disbursementAmount())
        .isEqualByComparingTo(BigDecimal.valueOf(150.25));
  }

  @Test
  void givenRequest_whenCreateMapped_thenSchemaMetadataIsVersion1AndPriorAuthorityJson() {
    CreatePriorAuthorityDraftCommand command = mapper.toCreateCommand(expertRequest());

    assertThat(command.schemaVersion()).isEqualTo(1);
    assertThat(command.schemaName()).isEqualTo("PriorAuthority.json");
  }

  @Test
  void givenRequest_whenCreateMapped_thenTimestampIsNonNull() {
    CreatePriorAuthorityDraftCommand command = mapper.toCreateCommand(expertRequest());

    assertThat(command.occurredAt()).isNotNull();
  }

  @Test
  void givenRequest_whenCreateMapped_thenContentIsSerialised() {
    CreatePriorAuthorityDraftCommand command = mapper.toCreateCommand(expertRequest());

    assertThat(command.serialisedRequest()).contains("Need expert assessment");
    assertThatCode(() -> JsonMapper.builder().build().readTree(command.serialisedRequest()))
        .doesNotThrowAnyException();
  }

  @Test
  void givenPriorAuthorityId_whenUpdateMapped_thenPriorAuthorityIdMatches() {
    UUID priorAuthorityId = UUID.randomUUID();
    UpdatePriorAuthorityDraftCommand command =
        mapper.toUpdateCommand(
            priorAuthorityId,
            SavePriorAuthorityDraftRequest.builder()
                .priorAuthorityType(PriorAuthorityType.EXPERT)
                .justification("Need expert assessment")
                .build());

    assertThat(command.priorAuthorityId()).isEqualTo(priorAuthorityId);
  }

  @Test
  void givenSerializationFailure_whenMapped_thenWrapsInIllegalStateException() throws Exception {
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    CreatePriorAuthorityDraftRequest request = expertRequest();
    SavePriorAuthorityDraftCommandMapper failingMapper =
        new SavePriorAuthorityDraftCommandMapper(objectMapper);

    when(objectMapper.writeValueAsString(request)).thenThrow(new JacksonException("boom") {});

    assertThatThrownBy(() -> failingMapper.toCreateCommand(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to serialise CreatePriorAuthorityDraftRequest")
        .hasCauseInstanceOf(JacksonException.class);
  }

  @Test
  void givenNullExpertCosts_whenMapped_thenExpertCostsIsNull() {
    CreatePriorAuthorityDraftCommand command =
        mapper.toCreateCommand(
            CreatePriorAuthorityDraftRequest.builder()
                .applicationId(UUID.randomUUID())
                .priorAuthorityType(PriorAuthorityType.EXPERT)
                .expertDetails(ExpertDetails.builder().expertType("Forensic Accountant").build())
                .build());

    assertThat(command.content().expertDetails().expertCosts()).isNull();
  }

  @Test
  void givenExpertCostsWithNullSubFields_whenMapped_thenNullsPreserved() {
    CreatePriorAuthorityDraftCommand command =
        mapper.toCreateCommand(
            CreatePriorAuthorityDraftRequest.builder()
                .applicationId(UUID.randomUUID())
                .priorAuthorityType(PriorAuthorityType.EXPERT)
                .expertDetails(
                    ExpertDetails.builder()
                        .expertType("Forensic Accountant")
                        .expertCosts(
                            ExpertCosts.builder()
                                .billingType(null)
                                .hourlyRate(null)
                                .timeRequested(null)
                                .totalAmount(null)
                                .apportionment(null)
                                .build())
                        .build())
                .build());

    assertThat(command.content().expertDetails().expertCosts().billingType()).isNull();
    assertThat(command.content().expertDetails().expertCosts().hourlyRate()).isNull();
    assertThat(command.content().expertDetails().expertCosts().timeRequested()).isNull();
    assertThat(command.content().expertDetails().expertCosts().apportionment()).isNull();
  }

  private CreatePriorAuthorityDraftRequest expertRequest() {
    return CreatePriorAuthorityDraftRequest.builder()
        .applicationId(UUID.randomUUID())
        .priorAuthorityType(PriorAuthorityType.EXPERT)
        .justification("Need expert assessment")
        .expertDetails(
            ExpertDetails.builder()
                .expertType("Pathologist")
                .expertFullName("Casey Expert")
                .expertPostcode("AB1 2CD")
                .expertCosts(
                    ExpertCosts.builder()
                        .billingType(BillingType.HOURLY)
                        .hourlyRate(300.0)
                        .timeRequested(TimeRequested.builder().hours(2).minutes(30).build())
                        .totalAmount(900.0)
                        .costsSharedWithOtherParties(true)
                        .apportionment(
                            Apportionment.builder()
                                .partiesSharingCosts(2)
                                .clientShareAmount(450.0)
                                .build())
                        .build())
                .build())
        .build();
  }

  private CreatePriorAuthorityDraftRequest counselRequest() {
    return CreatePriorAuthorityDraftRequest.builder()
        .applicationId(UUID.randomUUID())
        .priorAuthorityType(PriorAuthorityType.COUNSEL)
        .justification("Need specialist counsel")
        .counselDetails(
            CounselDetails.builder().counselType(CounselType.KINGS_COUNSEL_ALONE).build())
        .build();
  }

  private CreatePriorAuthorityDraftRequest disbursementRequest() {
    return CreatePriorAuthorityDraftRequest.builder()
        .applicationId(UUID.randomUUID())
        .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
        .justification("Need interpreter costs")
        .disbursementDetails(
            DisbursementDetails.builder()
                .disbursementPurpose("Interpreter")
                .disbursementAmount(150.25)
                .build())
        .build();
  }
}
