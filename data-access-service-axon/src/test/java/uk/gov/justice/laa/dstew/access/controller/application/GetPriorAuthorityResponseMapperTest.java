package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.ApportionmentInformation;
import uk.gov.justice.laa.dstew.access.content.priorauthority.Apportionment;
import uk.gov.justice.laa.dstew.access.content.priorauthority.BillingType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertCosts;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;

class GetPriorAuthorityResponseMapperTest {

  private final GetPriorAuthorityResponseMapper mapper = new GetPriorAuthorityResponseMapper();

  @Test
  void givenExpertResult_whenMapped_thenConvertsUseCaseTypesToGeneratedApiTypes() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .justification("Expert is required")
            .status("SUBMITTED")
            .decisionDetails(
                new PriorAuthorityDataPayload.DecisionDetails(
                    "GRANTED", "Decision recorded", null, null, null, null, null, "{}"))
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .expertDetails(
                new ExpertDetails(
                    "PSYCHIATRIST",
                    "Jane Doe",
                    "AB1 2CD",
                    new ExpertCosts(
                        BillingType.HOURLY,
                        BigDecimal.valueOf(150),
                        new TimeRequested(2, 30),
                        BigDecimal.valueOf(300),
                        true,
                        new Apportionment(2, BigDecimal.valueOf(150)))))
            .build();

    var response = mapper.toResponse(result);

    assertThat(response.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(response.getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getPriorAuthorityType().getValue()).isEqualTo("EXPERT");
    assertThat(response.getStatus()).isEqualTo(PriorAuthorityResponse.StatusEnum.SUBMITTED);
    assertThat(response.getDecision().getValue()).isEqualTo("GRANTED");
    assertThat(response.getDecisionJustification()).isEqualTo("Decision recorded");
    assertThat(response.getExpertDetails().getExpertCosts().getBillingType().getValue())
        .isEqualTo("HOURLY");
    assertThat(response.getExpertDetails().getExpertCosts().getHourlyRate())
        .isEqualByComparingTo(BigDecimal.valueOf(150.0));
    assertThat(response.getExpertDetails().getExpertCosts().getTimeRequested().getHours())
        .isEqualTo(2);
    assertThat(response.getExpertDetails().getExpertCosts().getTimeRequested().getMinutes())
        .isEqualTo(30);
    assertThat(
            response.getExpertDetails().getExpertCosts().getApportionment().getClientShareAmount())
        .isEqualByComparingTo(BigDecimal.valueOf(150.0));
  }

  @Test
  void givenCounselAndDisbursementResults_whenMapped_thenMapsTheirDetails() {
    PriorAuthorityResult counsel =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("Counsel is required")
            .status("SUBMITTED")
            .priorAuthorityType(PriorAuthorityType.COUNSEL)
            .counselDetails(new CounselDetails(CounselType.TWO_JUNIOR_COUNSEL))
            .build();
    PriorAuthorityResult disbursement =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("Travel is required")
            .status("SUBMITTED")
            .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
            .disbursementDetails(new DisbursementDetails("Travel", BigDecimal.TEN))
            .build();

    var counselResponse = mapper.toResponse(counsel);
    var disbursementResponse = mapper.toResponse(disbursement);

    assertThat(counselResponse.getCounselDetails().getCounselType().getValue())
        .isEqualTo("TWO_JUNIOR_COUNSEL");
    assertThat(disbursementResponse.getDisbursementDetails().getDisbursementAmount())
        .isEqualByComparingTo(BigDecimal.valueOf(10.0));
  }

  @Test
  void givenResultWithOptionalValuesAbsent_whenMapped_thenLeavesApiValuesNull() {
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("")
            .build();

    var response = mapper.toResponse(result);

    assertThat(response.getPriorAuthorityType()).isNull();
    assertThat(response.getExpertDetails()).isNull();
    assertThat(response.getCounselDetails()).isNull();
    assertThat(response.getDisbursementDetails()).isNull();
  }

  @Test
  void givenResultWithBlankOrInvalidDecisionAndNullStatus_whenMapped_thenLeavesThoseValuesNull() {
    PriorAuthorityResult blankDecisionResult =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .decisionDetails(
                new PriorAuthorityDataPayload.DecisionDetails(
                    "   ", null, null, null, null, null, null, "{}"))
            .build();
    PriorAuthorityResult invalidDecisionResult =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .status("SUBMITTED")
            .decisionDetails(
                new PriorAuthorityDataPayload.DecisionDetails(
                    "NOT_A_REAL_DECISION", null, null, null, null, null, null, "{}"))
            .build();

    var blankResponse = mapper.toResponse(blankDecisionResult);
    var invalidResponse = mapper.toResponse(invalidDecisionResult);

    assertThat(blankResponse.getStatus()).isNull();
    assertThat(blankResponse.getDecision()).isNull();
    assertThat(invalidResponse.getDecision()).isNull();
  }

  @Test
  void givenDecisionDetailsAndDocumentsWithNullType_whenMapped_thenMapsNestedFields() {
    Instant decisionDate = Instant.parse("2026-09-08T12:00:00Z");
    UUID documentId = UUID.randomUUID();
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("justification")
            .status("DECIDED")
            .decisionDetails(
                new PriorAuthorityDataPayload.DecisionDetails(
                    "GRANTED",
                    "Decision recorded",
                    BigDecimal.valueOf(99.99),
                    decisionDate,
                    ExpertFeeInformation.builder()
                        .newFixedRateAmount(BigDecimal.valueOf(250))
                        .newHourlyRateAmount(BigDecimal.valueOf(175))
                        .build(),
                    DisbursementInformation.builder().newAmount(BigDecimal.valueOf(50)).build(),
                    ApportionmentInformation.builder()
                        .newClientShareAmount(BigDecimal.valueOf(25))
                        .build(),
                    "{}"))
            .uploadedDocuments(
                List.of(
                    new UploadDocument(
                        documentId,
                        null,
                        null,
                        42L,
                        "application/pdf",
                        "checksum",
                        "CIVIL_APPLY",
                        false)))
            .documentFilenames(Map.of(documentId, "evidence.pdf"))
            .build();

    var response = mapper.toResponse(result);

    assertThat(response.getStatus()).isEqualTo(PriorAuthorityResponse.StatusEnum.DECIDED);
    assertThat(response.getDecision().getValue()).isEqualTo("GRANTED");
    assertThat(response.getDecisionDetails().getDecision().getValue()).isEqualTo("GRANTED");
    assertThat(response.getDecisionDetails().getDecisionJustification())
        .isEqualTo("Decision recorded");
    assertThat(response.getDecisionDetails().getAmountGranted()).isEqualByComparingTo("99.99");
    assertThat(response.getDecisionDetails().getDateGranted())
        .isEqualTo(decisionDate.atOffset(ZoneOffset.UTC));
    assertThat(response.getDecisionDetails().getExpert().getNewFixedRateAmount())
        .isEqualByComparingTo("250");
    assertThat(response.getDecisionDetails().getExpert().getNewHourlyRateAmount())
        .isEqualByComparingTo("175");
    assertThat(response.getDecisionDetails().getDisbursement().getNewAmount())
        .isEqualByComparingTo("50");
    assertThat(response.getDecisionDetails().getApportionment().getClientShareAmount())
        .isEqualByComparingTo("25");
    assertThat(response.getUploadedDocuments()).hasSize(1);
    assertThat(response.getUploadedDocuments().get(0).getDocumentType()).isNull();
    assertThat(response.getUploadedDocuments().get(0).getUploadedAt()).isNull();
  }

  @Test
  void givenDocumentsWithTheSameFilename_whenMapped_thenKeepsEachDocumentIdentifiable() {
    UUID firstDocumentId = UUID.randomUUID();
    UUID secondDocumentId = UUID.randomUUID();
    Instant firstUploadedAt = Instant.parse("2026-09-08T12:00:00Z");
    Instant secondUploadedAt = Instant.parse("2026-09-08T12:01:00Z");
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .uploadedDocuments(
                List.of(
                    new UploadDocument(
                        firstDocumentId,
                        "GATEWAY_EVIDENCE",
                        firstUploadedAt,
                        42L,
                        "application/pdf",
                        "first-checksum",
                        "CIVIL_APPLY",
                        false),
                    new UploadDocument(
                        secondDocumentId,
                        "GATEWAY_EVIDENCE",
                        secondUploadedAt,
                        84L,
                        "application/pdf",
                        "second-checksum",
                        "CIVIL_APPLY",
                        false)))
            .documentFilenames(
                Map.of(firstDocumentId, "evidence.pdf", secondDocumentId, "evidence.pdf"))
            .build();

    var response = mapper.toResponse(result);

    assertThat(response.getUploadedDocuments())
        .extracting("documentId")
        .containsExactly(firstDocumentId, secondDocumentId);
    assertThat(response.getUploadedDocuments())
        .extracting("fileName")
        .containsExactly("evidence.pdf", "evidence.pdf");
    assertThat(response.getUploadedDocuments())
        .extracting("uploadedAt")
        .containsExactly(
            firstUploadedAt.atOffset(ZoneOffset.UTC), secondUploadedAt.atOffset(ZoneOffset.UTC));
    assertThat(response.getUploadedDocuments())
        .extracting("documentType", "fileType", "mediaType", "size", "sourceService", "checksum")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                PriorAuthorityDocumentType.GATEWAY_EVIDENCE,
                "PDF",
                "application/pdf",
                42L,
                "CIVIL_APPLY",
                "first-checksum"),
            org.assertj.core.groups.Tuple.tuple(
                PriorAuthorityDocumentType.GATEWAY_EVIDENCE,
                "PDF",
                "application/pdf",
                84L,
                "CIVIL_APPLY",
                "second-checksum"));
  }

  @Test
  void givenDecisionDetailsWithNullNestedValues_whenMapped_thenLeavesNestedDecisionValuesNull() {
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .status("DECIDED")
            .decisionDetails(
                new PriorAuthorityDataPayload.DecisionDetails(
                    null, null, null, null, null, null, null, "{}"))
            .build();

    var response = mapper.toResponse(result);

    assertThat(response.getDecisionDetails()).isNotNull();
    assertThat(response.getDecisionDetails().getDecision()).isNull();
    assertThat(response.getDecisionDetails().getDecisionJustification()).isNull();
    assertThat(response.getDecisionDetails().getAmountGranted()).isNull();
    assertThat(response.getDecisionDetails().getDateGranted()).isNull();
    assertThat(response.getDecisionDetails().getExpert()).isNull();
    assertThat(response.getDecisionDetails().getDisbursement()).isNull();
    assertThat(response.getDecisionDetails().getApportionment()).isNull();
  }

  @Test
  void givenNullUploadedDocuments_whenMapped_thenLeavesUploadedDocumentsNull() {
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("justification")
            .status("DRAFT")
            .build();

    var response = mapper.toResponse(result);

    assertThat(response.getUploadedDocuments()).isNull();
  }

  @Test
  void givenNullableNestedValues_whenMapped_thenLeavesTheirApiValuesNull() {
    PriorAuthorityResult expert =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("Expert is required")
            .status("SUBMITTED")
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .expertDetails(
                new ExpertDetails(
                    "PSYCHIATRIST",
                    "Jane Doe",
                    "AB1 2CD",
                    new ExpertCosts(null, null, null, null, null, null)))
            .build();
    PriorAuthorityResult counsel =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("Counsel is required")
            .status("SUBMITTED")
            .priorAuthorityType(PriorAuthorityType.COUNSEL)
            .counselDetails(new CounselDetails(null))
            .build();
    PriorAuthorityResult disbursement =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("Disbursement is required")
            .status("SUBMITTED")
            .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
            .disbursementDetails(new DisbursementDetails("Travel", null))
            .build();

    var expertResponse = mapper.toResponse(expert);
    var counselResponse = mapper.toResponse(counsel);
    var disbursementResponse = mapper.toResponse(disbursement);

    assertThat(expertResponse.getExpertDetails().getExpertCosts().getBillingType()).isNull();
    assertThat(expertResponse.getExpertDetails().getExpertCosts().getHourlyRate()).isNull();
    assertThat(expertResponse.getExpertDetails().getExpertCosts().getTimeRequested()).isNull();
    assertThat(expertResponse.getExpertDetails().getExpertCosts().getTotalAmount()).isNull();
    assertThat(expertResponse.getExpertDetails().getExpertCosts().getApportionment()).isNull();
    assertThat(counselResponse.getCounselDetails().getCounselType()).isNull();
    assertThat(disbursementResponse.getDisbursementDetails().getDisbursementAmount()).isNull();
  }

  @Test
  void givenUploadedDocuments_whenMapped_thenMapsDocumentFieldsAndUtcTimestamps() {
    Instant uploadedAt = Instant.parse("2026-09-08T12:30:00Z");
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .priorAuthorityId(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .justification("justification")
            .status("DRAFT")
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .uploadedDocuments(
                List.of(
                    new UploadDocument(
                        firstId,
                        "GATEWAY_EVIDENCE",
                        uploadedAt,
                        12L,
                        "application/pdf",
                        "checksum-one",
                        "CIVIL_APPLY",
                        false),
                    new UploadDocument(
                        secondId,
                        "MERITS_REPORT",
                        null,
                        8L,
                        "application/pdf",
                        "checksum-two",
                        "CIVIL_DECIDE",
                        false)))
            .documentFilenames(Map.of(firstId, "a.pdf", secondId, "b.pdf"))
            .build();

    var response = mapper.toResponse(result);

    assertThat(response.getUploadedDocuments()).hasSize(2);
    assertThat(response.getUploadedDocuments().get(0).getDocumentType())
        .isEqualTo(
            uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType.GATEWAY_EVIDENCE);
    assertThat(response.getUploadedDocuments().get(0).getFileType()).isEqualTo("PDF");
    assertThat(response.getUploadedDocuments().get(0).getFileName()).isEqualTo("a.pdf");
    assertThat(response.getUploadedDocuments().get(0).getMediaType()).isEqualTo("application/pdf");
    assertThat(response.getUploadedDocuments().get(0).getSize()).isEqualTo(12L);
    assertThat(response.getUploadedDocuments().get(0).getSourceService()).isEqualTo("CIVIL_APPLY");
    assertThat(response.getUploadedDocuments().get(0).getChecksum()).isEqualTo("checksum-one");
    assertThat(response.getUploadedDocuments().get(0).getUploadedAt())
        .isEqualTo(uploadedAt.atOffset(ZoneOffset.UTC));
    assertThat(response.getUploadedDocuments().get(1).getUploadedAt()).isNull();
  }

  @Test
  void givenDeletedDocument_whenMapped_thenOmitsItAndKeepsUploadOrder() {
    UUID firstId = UUID.randomUUID();
    UUID deletedId = UUID.randomUUID();
    UUID lastId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-08T12:30:00Z");
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .uploadedDocuments(
                List.of(
                    document(firstId, uploadedAt.plusSeconds(10), false),
                    document(deletedId, uploadedAt, true),
                    document(lastId, uploadedAt, false)))
            .documentFilenames(Map.of(firstId, "first.pdf", lastId, "last.pdf"))
            .build();

    var response = mapper.toResponse(result);

    assertThat(response.getUploadedDocuments())
        .extracting("documentId", "fileName")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(firstId, "first.pdf"),
            org.assertj.core.groups.Tuple.tuple(lastId, "last.pdf"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenNoActiveDocuments_whenMapped_thenPreservesNullOrEmptyContract(boolean anyUploaded) {
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .uploadedDocuments(
                anyUploaded ? List.of(document(UUID.randomUUID(), Instant.now(), true)) : List.of())
            .build();

    var documents = mapper.toResponse(result).getUploadedDocuments();

    if (anyUploaded) {
      assertThat(documents).isEmpty();
    } else {
      assertThat(documents).isNull();
    }
  }

  private UploadDocument document(UUID documentId, Instant uploadedAt, boolean deleted) {
    return new UploadDocument(
        documentId,
        "GATEWAY_EVIDENCE",
        uploadedAt,
        12L,
        "application/pdf",
        "checksum",
        "CIVIL_APPLY",
        deleted);
  }
}
