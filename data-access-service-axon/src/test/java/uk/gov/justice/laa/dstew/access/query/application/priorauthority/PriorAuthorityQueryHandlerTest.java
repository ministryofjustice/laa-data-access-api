package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityStateEvents.aggregateOf;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.COUNSEL;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.DISBURSEMENT;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.EXPERT;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
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
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityQueryHandlerTest {

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final Instant STARTED_AT = Instant.parse("2026-09-10T09:00:00Z");

  @Mock private PriorAuthorityDataStore dataStore;
  @Mock private PriorAuthorityDraftStore draftStore;
  @InjectMocks private PriorAuthorityQueryHandler handler;

  @Test
  void givenDraft_whenQueried_thenHydratesDraftContentAndDocumentFilenames() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = STARTED_AT.plusSeconds(60);
    PriorAuthorityAggregate aggregate =
        aggregateOf(
            List.of(started(priorAuthorityId), uploaded(priorAuthorityId, documentId, uploadedAt)));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                payload(priorAuthorityId, content(EXPERT))
                    .withDocumentFilename(documentId, "evidence.pdf")));

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
    verifyNoInteractions(dataStore);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUBMITTED", "DECIDED"})
  void givenSubmittedOrDecided_whenQueried_thenHydratesContentAtEventDataVersion(String status) {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2026-09-11T10:00:00Z");
    List<Object> events = new ArrayList<>(List.of(started(priorAuthorityId)));
    events.add(submitted(priorAuthorityId, 1L));
    long dataVersion = 1L;
    if ("DECIDED".equals(status)) {
      events.add(decided(priorAuthorityId, 4L));
      dataVersion = 4L;
    }
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            COUNSEL,
            "Counsel is required",
            null,
            new CounselDetails(CounselType.TWO_JUNIOR_COUNSEL),
            null);
    when(dataStore.get(priorAuthorityId, dataVersion))
        .thenReturn(
            new PriorAuthorityDataPayload(
                priorAuthorityId, APPLICATION_ID, content, "{}", submittedAt));

    PriorAuthorityResult result =
        handler.handle(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId), aggregateOf(events));

    assertThat(result.status()).isEqualTo(status);
    assertThat(result.submittedAt()).isEqualTo(submittedAt);
    assertThat(result.justification()).isEqualTo("Counsel is required");
    assertThat(result.counselDetails().counselType()).isEqualTo(CounselType.TWO_JUNIOR_COUNSEL);
    verifyNoInteractions(draftStore);
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
    verifyNoInteractions(dataStore, draftStore);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1A001B", "OTHER"})
  void givenOfficeScope_whenQueried_thenReturnsOnlyPermittedOffice(String permittedOffice) {
    UUID priorAuthorityId = UUID.randomUUID();
    if ("1A001B".equals(permittedOffice)) {
      when(draftStore.find(priorAuthorityId))
          .thenReturn(Optional.of(payload(priorAuthorityId, content(EXPERT))));
    }

    PriorAuthorityResult result =
        handler.handle(
            new FindPriorAuthorityByPriorAuthorityIdQuery(
                priorAuthorityId, new OfficeCodeReadAccessScope(Set.of(permittedOffice))),
            aggregateOf(List.of(started(priorAuthorityId))));

    assertThat(result != null).isEqualTo("1A001B".equals(permittedOffice));
  }

  @Test
  void givenSubmittedContentMissing_whenQueried_thenReturnsNull() {
    UUID priorAuthorityId = UUID.randomUUID();

    assertThat(
            handler.handle(
                new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                aggregateOf(List.of(started(priorAuthorityId), submitted(priorAuthorityId, 1L)))))
        .isNull();
  }

  @Test
  void givenDecisionStoredInPayload_whenQueried_thenReturnsDecisionDetails() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(dataStore.get(priorAuthorityId, 2L))
        .thenReturn(
            new PriorAuthorityDataPayload(
                priorAuthorityId,
                APPLICATION_ID,
                content(EXPERT),
                "{}",
                Instant.now(),
                new PriorAuthorityDataPayload.DecisionDetails(
                    "GRANTED",
                    "Decision recorded",
                    BigDecimal.valueOf(99.99),
                    Instant.parse("2026-09-08T12:00:00Z"),
                    null,
                    DisbursementInformation.builder().build(),
                    null,
                    "{\"decision\":\"GRANTED\"}"),
                null,
                null));

    PriorAuthorityResult result =
        handler.handle(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
            aggregateOf(List.of(started(priorAuthorityId), decided(priorAuthorityId, 2L))));

    assertThat(result.status()).isEqualTo("DECIDED");
    assertThat(result.decisionDetails().decision()).isEqualTo("GRANTED");
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
                uploaded(priorAuthorityId, documentId, uploadedAt),
                new PriorAuthorityDocumentTypeUpdatedEvent(
                    priorAuthorityId, documentId, "EXPERT_REPORT", uploadedAt, APPLICATION_ID)));
    PriorAuthorityDataPayload payload =
        payload(priorAuthorityId, null).withDocumentFilename(documentId, "evidence.PDF");
    switch (status) {
      case "DRAFT" -> when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(payload));
      case "SUBMITTED" -> {
        events.add(submitted(priorAuthorityId, 2L));
        when(dataStore.get(priorAuthorityId, 2L)).thenReturn(payload);
      }
      default -> {
        events.add(submitted(priorAuthorityId, 1L));
        events.add(decided(priorAuthorityId, 2L));
        when(dataStore.get(priorAuthorityId, 2L)).thenReturn(payload);
      }
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
    verifyNoInteractions(draftStore, dataStore);
  }

  @Test
  void givenDocumentWithoutFilename_whenDocumentQueried_thenReturnsMetadata() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

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
    when(dataStore.get(priorAuthorityId, 1L)).thenReturn(payload(priorAuthorityId, content));

    return handler.handle(
        new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
        aggregateOf(List.of(started(priorAuthorityId), submitted(priorAuthorityId, 1L))));
  }

  private static PriorAuthorityContent content(PriorAuthorityType type) {
    return new PriorAuthorityContent(type, "Expert required", null, null, null);
  }

  private static PriorAuthorityDataPayload payload(
      UUID priorAuthorityId, PriorAuthorityContent content) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId, APPLICATION_ID, content, "{}", Instant.now());
  }

  private static PriorAuthorityDraftStartedEvent started(UUID priorAuthorityId) {
    return new PriorAuthorityDraftStartedEvent(
        priorAuthorityId, APPLICATION_ID, EXPERT.name(), 1, STARTED_AT, "1A001B");
  }

  private static PriorAuthoritySubmittedEvent submitted(UUID priorAuthorityId, long dataVersion) {
    return new PriorAuthoritySubmittedEvent(
        priorAuthorityId,
        APPLICATION_ID,
        EXPERT.name(),
        1,
        dataVersion,
        0L,
        STARTED_AT.plusSeconds(120));
  }

  private static PriorAuthorityDecisionMadeEvent decided(UUID priorAuthorityId, long dataVersion) {
    return new PriorAuthorityDecisionMadeEvent(
        priorAuthorityId,
        APPLICATION_ID,
        EXPERT.name(),
        dataVersion,
        "GRANTED",
        "Decision recorded",
        null,
        null,
        STARTED_AT.plusSeconds(180));
  }

  private static PriorAuthorityDocumentUploadedEvent uploaded(
      UUID priorAuthorityId, UUID documentId, Instant uploadedAt) {
    return new PriorAuthorityDocumentUploadedEvent(
        priorAuthorityId,
        documentId,
        uploadedAt,
        12L,
        "application/pdf",
        "checksum",
        APPLICATION_ID,
        "CIVIL_APPLY",
        ".PDF");
  }
}
