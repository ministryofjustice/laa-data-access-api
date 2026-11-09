package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityStateEvents.aggregateOf;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.COUNSEL;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.DISBURSEMENT;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.EXPERT;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.Apportionment;
import uk.gov.justice.laa.dstew.access.content.priorauthority.BillingType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertCosts;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityQueryHandlerTest {

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final Instant STARTED_AT = Instant.parse("2026-09-10T09:00:00Z");
  private static final Instant SUBMITTED_AT = STARTED_AT.plusSeconds(120);

  private final PriorAuthorityQueryHandler handler = new PriorAuthorityQueryHandler();

  @Test
  void givenDraft_whenQueried_thenHydratesDraftContentAndDocumentFilenames() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = STARTED_AT.plusSeconds(60);
    PriorAuthorityAggregate aggregate =
        aggregateOf(
            List.of(
                started(priorAuthorityId),
                uploaded(priorAuthorityId, documentId, uploadedAt, "evidence.pdf")));

    PriorAuthorityResult result =
        handler.handle(new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId), aggregate);

    assertThat(result.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(result.applicationId()).isEqualTo(APPLICATION_ID);
    assertThat(result.status()).isEqualTo("DRAFT");
    assertThat(result.submittedAt()).isNull();
    assertThat(result.priorAuthorityType()).isEqualTo(EXPERT);
    assertThat(result.uploadedDocuments())
        .extracting(DocumentMetadata::documentId)
        .containsExactly(documentId);
    assertThat(result.documentFilenames())
        .containsExactlyEntriesOf(Map.of(documentId, "evidence.pdf"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "DECIDED"})
  void givenSubmittedOrDecided_whenQueried_thenHydratesSealedDraftContent(String status) {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            COUNSEL,
            "Counsel is required",
            null,
            new CounselDetails(CounselType.TWO_JUNIOR_COUNSEL),
            null);
    List<Object> events = new ArrayList<>(List.of(started(priorAuthorityId, content)));
    events.add(submitted(priorAuthorityId));
    if ("DECIDED".equals(status)) {
      events.add(decided(priorAuthorityId));
    }

    PriorAuthorityResult result =
        handler.handle(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId), aggregateOf(events));

    assertThat(result.status()).isEqualTo(status);
    assertThat(result.submittedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(result.justification()).isEqualTo("Counsel is required");
    assertThat(result.counselDetails().counselType()).isEqualTo(CounselType.TWO_JUNIOR_COUNSEL);
  }

  @Test
  void givenMissingAggregate_whenQueried_thenReturnsNullWithoutReadingContent() {
    UUID priorAuthorityId = UUID.randomUUID();

    assertThat(
            handler.handle(new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId), null))
        .isNull();
    assertThat(
            handler.handle(
                new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                aggregateOf(List.of())))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1A001B", "OTHER"})
  void givenOfficeScope_whenQueried_thenReturnsOnlyPermittedOffice(String permittedOffice) {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityResult result =
        handler.handle(
            new FindPriorAuthorityByPriorAuthorityIdQuery(
                priorAuthorityId, new OfficeCodeReadAccessScope(Set.of(permittedOffice))),
            aggregateOf(List.of(started(priorAuthorityId))));

    assertThat(result != null).isEqualTo("1A001B".equals(permittedOffice));
  }

  @Test
  void givenErasedDraftContent_whenQueried_thenReturnsNull() {
    UUID priorAuthorityId = UUID.randomUUID();

    assertThat(
            handler.handle(
                new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                aggregateOf(
                    List.of(
                        new PriorAuthorityDraftStartedEvent(
                            priorAuthorityId,
                            APPLICATION_ID,
                            EXPERT.name(),
                            1,
                            STARTED_AT,
                            "1A001B")))))
        .isNull();
  }

  @Test
  void givenErasedSubmittedContent_whenQueried_thenReturnsNull() {
    UUID priorAuthorityId = UUID.randomUUID();

    assertThat(
            handler.handle(
                new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                aggregateOf(
                    List.of(
                        new PriorAuthorityDraftStartedEvent(
                            priorAuthorityId,
                            APPLICATION_ID,
                            EXPERT.name(),
                            1,
                            STARTED_AT,
                            "1A001B"),
                        submitted(priorAuthorityId)))))
        .isNull();
  }

  @Test
  void givenDecision_whenQueried_thenReturnsDecisionDetailsFromTheDecisionEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    DisbursementInformation disbursement = DisbursementInformation.builder().build();

    PriorAuthorityResult result =
        handler.handle(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
            aggregateOf(
                List.of(
                    started(priorAuthorityId),
                    submitted(priorAuthorityId),
                    new PriorAuthorityDecisionMadeEvent(
                        priorAuthorityId,
                        APPLICATION_ID,
                        EXPERT.name(),
                        "GRANTED",
                        "Decision recorded",
                        BigDecimal.valueOf(99.99),
                        Instant.parse("2026-09-08T12:00:00Z"),
                        null,
                        disbursement,
                        null,
                        STARTED_AT.plusSeconds(180)))));

    assertThat(result.status()).isEqualTo("DECIDED");
    assertThat(result.decisionDetails())
        .isEqualTo(
            new PriorAuthorityDataPayload.DecisionDetails(
                "GRANTED",
                "Decision recorded",
                BigDecimal.valueOf(99.99),
                Instant.parse("2026-09-08T12:00:00Z"),
                null,
                disbursement,
                null));
  }

  @Test
  void givenExpertDetails_whenQueried_thenHydratesOnlyExpertDetails() {
    ExpertCosts expertCosts =
        new ExpertCosts(
            BillingType.HOURLY,
            new BigDecimal("125.50"),
            new TimeRequested(2, 30),
            new BigDecimal("313.75"),
            true,
            new Apportionment(3, new BigDecimal("104.58")));
    PriorAuthorityResult expertResult =
        handleContent(
            new PriorAuthorityContent(
                EXPERT,
                "Expert required",
                new ExpertDetails("Accountant", "Ada Lovelace", "SW1A 1AA", expertCosts),
                null,
                null));

    assertThat(expertResult.priorAuthorityType()).isEqualTo(EXPERT);
    assertThat(expertResult.expertDetails().expertType()).isEqualTo("Accountant");
    assertThat(expertResult.expertDetails().expertFullName()).isEqualTo("Ada Lovelace");
    assertThat(expertResult.expertDetails().expertPostcode()).isEqualTo("SW1A 1AA");
    assertThat(expertResult.expertDetails().expertCosts()).isEqualTo(expertCosts);
    assertThat(expertResult.counselDetails()).isNull();
    assertThat(expertResult.disbursementDetails()).isNull();
  }

  @Test
  void givenCounselDetails_whenQueried_thenHydratesOnlyCounselDetails() {
    PriorAuthorityResult counselResult =
        handleContent(
            new PriorAuthorityContent(
                COUNSEL,
                "Counsel required",
                null,
                new CounselDetails(CounselType.TWO_JUNIOR_COUNSEL),
                null));

    assertThat(counselResult.counselDetails().counselType())
        .isEqualTo(CounselType.TWO_JUNIOR_COUNSEL);
    assertThat(counselResult.expertDetails()).isNull();
    assertThat(counselResult.disbursementDetails()).isNull();
  }

  @Test
  void givenDisbursementDetails_whenQueried_thenHydratesOnlyDisbursementDetails() {
    PriorAuthorityResult disbursementResult =
        handleContent(
            new PriorAuthorityContent(
                DISBURSEMENT,
                "Disbursement required",
                null,
                null,
                new DisbursementDetails("Travel", BigDecimal.TEN)));

    assertThat(disbursementResult.disbursementDetails().disbursementPurpose()).isEqualTo("Travel");
    assertThat(disbursementResult.disbursementDetails().disbursementAmount())
        .isEqualTo(BigDecimal.TEN);
    assertThat(disbursementResult.expertDetails()).isNull();
    assertThat(disbursementResult.counselDetails()).isNull();
  }

  @ParameterizedTest
  @EnumSource(PriorAuthorityType.class)
  void givenTypeWithAbsentDetails_whenQueried_thenHydratedDetailsAreNull(PriorAuthorityType type) {
    PriorAuthorityResult result =
        handleContent(new PriorAuthorityContent(type, "Required", null, null, null));

    assertThat(result.expertDetails()).isNull();
    assertThat(result.counselDetails()).isNull();
    assertThat(result.disbursementDetails()).isNull();
  }

  @Test
  void givenNullType_whenQueried_thenPriorAuthorityTypeIsNull() {
    PriorAuthorityResult result =
        handleContent(new PriorAuthorityContent(null, "Required", null, null, null));

    assertThat(result.priorAuthorityType()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"DRAFT", "SUBMITTED", "DECIDED"})
  void givenOwnedDocument_whenDocumentQueried_thenHydratesTypeAndFilename(String status) {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = STARTED_AT.plusSeconds(60);
    List<Object> events =
        new ArrayList<>(
            List.of(
                started(priorAuthorityId),
                uploaded(priorAuthorityId, documentId, uploadedAt, "evidence.PDF"),
                new PriorAuthorityDocumentTypeUpdatedEvent(
                    priorAuthorityId, documentId, "EXPERT_REPORT", uploadedAt, APPLICATION_ID)));
    if (!"DRAFT".equals(status)) {
      events.add(submitted(priorAuthorityId));
    }
    if ("DECIDED".equals(status)) {
      events.add(decided(priorAuthorityId));
    }

    assertThat(
            handler.handle(
                new FindPriorAuthorityDocumentQuery(priorAuthorityId, documentId),
                aggregateOf(events)))
        .isEqualTo(
            new EvidenceDocument(
                documentId,
                "EXPERT_REPORT",
                "evidence.PDF",
                null,
                "application/pdf",
                12L,
                uploadedAt,
                "CIVIL_APPLY",
                "checksum",
                ".PDF"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing owner", "unknown document", "deleted"})
  void givenUnavailableDocument_whenDocumentQueried_thenReturnsNullWithoutHydration(
      String scenario) {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    List<Object> events = new ArrayList<>();
    if (!"missing owner".equals(scenario)) {
      events.add(started(priorAuthorityId));
      events.add(
          uploaded(
              priorAuthorityId,
              "unknown document".equals(scenario) ? UUID.randomUUID() : documentId,
              STARTED_AT));
    }
    if ("deleted".equals(scenario)) {
      events.add(
          new PriorAuthorityDocumentDeletedEvent(
              priorAuthorityId, documentId, STARTED_AT, APPLICATION_ID));
    }

    assertThat(
            handler.handle(
                new FindPriorAuthorityDocumentQuery(priorAuthorityId, documentId),
                aggregateOf(events)))
        .isNull();
  }

  @Test
  void givenDocumentWithoutFilename_whenDocumentQueried_thenReturnsMetadata() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    EvidenceDocument document =
        handler.handle(
            new FindPriorAuthorityDocumentQuery(priorAuthorityId, documentId),
            aggregateOf(
                List.of(
                    started(priorAuthorityId),
                    uploaded(priorAuthorityId, documentId, STARTED_AT))));

    assertThat(document.documentId()).isEqualTo(documentId);
    assertThat(document.fileName()).isNull();
    assertThat(document.storageFilename()).isEqualTo(documentId + ".PDF");
  }

  private PriorAuthorityResult handleContent(PriorAuthorityContent content) {
    UUID priorAuthorityId = UUID.randomUUID();

    return handler.handle(
        new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
        aggregateOf(List.of(started(priorAuthorityId, content), submitted(priorAuthorityId))));
  }

  private static PriorAuthorityContent content(PriorAuthorityType type) {
    return new PriorAuthorityContent(type, "Expert required", null, null, null);
  }

  private static PriorAuthorityDraftStartedEvent started(UUID priorAuthorityId) {
    return started(priorAuthorityId, content(EXPERT));
  }

  private static PriorAuthorityDraftStartedEvent started(
      UUID priorAuthorityId, PriorAuthorityContent content) {
    return new PriorAuthorityDraftStartedEvent(
        priorAuthorityId,
        APPLICATION_ID,
        EXPERT.name(),
        1,
        STARTED_AT,
        "1A001B",
        Erasable.of(content),
        Erasable.of("{}"));
  }

  private static PriorAuthoritySubmittedEvent submitted(UUID priorAuthorityId) {
    return new PriorAuthoritySubmittedEvent(
        priorAuthorityId, APPLICATION_ID, EXPERT.name(), 1, null, SUBMITTED_AT);
  }

  private static PriorAuthorityDecisionMadeEvent decided(UUID priorAuthorityId) {
    return new PriorAuthorityDecisionMadeEvent(
        priorAuthorityId,
        APPLICATION_ID,
        EXPERT.name(),
        "GRANTED",
        "Decision recorded",
        null,
        null,
        STARTED_AT.plusSeconds(180));
  }

  private static PriorAuthorityDocumentUploadedEvent uploaded(
      UUID priorAuthorityId, UUID documentId, Instant uploadedAt) {
    return uploaded(priorAuthorityId, documentId, uploadedAt, null);
  }

  private static PriorAuthorityDocumentUploadedEvent uploaded(
      UUID priorAuthorityId, UUID documentId, Instant uploadedAt, String originalFilename) {
    return new PriorAuthorityDocumentUploadedEvent(
        priorAuthorityId,
        documentId,
        uploadedAt,
        12L,
        "application/pdf",
        "checksum",
        APPLICATION_ID,
        "CIVIL_APPLY",
        ".PDF",
        originalFilename);
  }
}
