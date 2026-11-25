package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
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
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;

class PriorAuthorityQueryHandlerTest {

  private static final UUID PRIOR_AUTHORITY_ID = UUID.randomUUID();
  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final String OFFICE_CODE = "1A001B";
  private static final Instant STARTED_AT = Instant.parse("2026-09-10T09:00:00Z");
  private static final Instant SUBMITTED_AT = Instant.parse("2026-09-11T10:00:00Z");
  private static final PriorAuthorityContent STARTED_CONTENT =
      new PriorAuthorityContent(EXPERT, "First justification", null, null, null);

  private final PriorAuthorityQueryHandler handler = new PriorAuthorityQueryHandler();

  @Test
  void givenUpdatedDraft_whenQueried_thenReturnsLastUpdateWithOfficeCodeAndFilenames() {
    UUID documentId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    PriorAuthorityContent updated =
        new PriorAuthorityContent(
            COUNSEL,
            "Updated justification",
            null,
            new CounselDetails(CounselType.TWO_JUNIOR_COUNSEL),
            null);
    PriorAuthorityAggregate aggregate =
        aggregateOf(
            started(STARTED_CONTENT),
            new PriorAuthorityDraftUpdatedEvent(
                PRIOR_AUTHORITY_ID,
                APPLICATION_ID,
                STARTED_AT.plusSeconds(10),
                Erasable.of(updated),
                Erasable.of("{\"updated\":true}")),
            uploaded(documentId, "evidence.pdf"),
            new WorkItemAssigned(
                PRIOR_AUTHORITY_ID,
                WorkItemType.PRIOR_AUTHORITY,
                APPLICATION_ID,
                0L,
                3L,
                caseworkerId,
                STARTED_AT.plusSeconds(30)));

    PriorAuthorityResult result = handler.handle(query(), aggregate);

    assertThat(result.priorAuthorityId()).isEqualTo(PRIOR_AUTHORITY_ID);
    assertThat(result.applicationId()).isEqualTo(APPLICATION_ID);
    assertThat(result.officeCode()).isEqualTo(OFFICE_CODE);
    assertThat(result.status()).isEqualTo("DRAFT");
    assertThat(result.submittedAt()).isNull();
    assertThat(result.justification()).isEqualTo("Updated justification");
    assertThat(result.priorAuthorityType()).isEqualTo(COUNSEL);
    assertThat(result.counselDetails().counselType()).isEqualTo(CounselType.TWO_JUNIOR_COUNSEL);
    assertThat(result.expertDetails()).isNull();
    assertThat(result.caseworkerId()).isEqualTo(caseworkerId);
    assertThat(result.assignmentVersion()).isEqualTo(3L);
    assertThat(result.uploadedDocuments()).hasSize(1);
    assertThat(result.documentFilenames())
        .containsExactlyEntriesOf(Map.of(documentId, "evidence.pdf"));
  }

  @Test
  void givenSubmitted_whenQueried_thenReturnsSubmittedContentAndRetainedFilenames() {
    UUID documentId = UUID.randomUUID();
    PriorAuthorityAggregate aggregate =
        aggregateOf(
            started(STARTED_CONTENT),
            uploaded(documentId, "evidence.pdf"),
            submitted(STARTED_CONTENT));

    PriorAuthorityResult result = handler.handle(query(), aggregate);

    assertThat(result.status()).isEqualTo("SUBMITTED");
    assertThat(result.officeCode()).isEqualTo(OFFICE_CODE);
    assertThat(result.submittedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(result.dataVersion()).isEqualTo(0L);
    assertThat(result.justification()).isEqualTo("First justification");
    assertThat(result.decisionDetails()).isNull();
    assertThat(result.documentFilenames())
        .containsExactlyEntriesOf(Map.of(documentId, "evidence.pdf"));
  }

  @Test
  void givenDecided_whenQueried_thenReturnsDecisionAtDecidedDataVersion() {
    PriorAuthorityAggregate aggregate =
        aggregateOf(
            started(STARTED_CONTENT),
            submitted(STARTED_CONTENT),
            new PriorAuthorityDecisionMadeEvent(
                PRIOR_AUTHORITY_ID,
                APPLICATION_ID,
                "EXPERT",
                1L,
                "GRANTED",
                "Decision recorded",
                BigDecimal.valueOf(99.99),
                SUBMITTED_AT.plusSeconds(60),
                SUBMITTED_AT.plusSeconds(60),
                null,
                null,
                null,
                "{\"decision\":\"GRANTED\"}"));

    PriorAuthorityResult result = handler.handle(query(), aggregate);

    assertThat(result.status()).isEqualTo("DECIDED");
    assertThat(result.dataVersion()).isEqualTo(1L);
    assertThat(result.submittedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(result.decisionDetails().decision()).isEqualTo("GRANTED");
  }

  @Test
  void givenExpertDetails_whenQueried_thenMapsOnlyExpertDetails() {
    ExpertCosts expertCosts =
        new ExpertCosts(
            BillingType.HOURLY,
            new BigDecimal("125.50"),
            new TimeRequested(2, 30),
            new BigDecimal("313.75"),
            true,
            new Apportionment(3, new BigDecimal("104.58")));
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            EXPERT,
            "Expert required",
            new ExpertDetails("Accountant", "Ada Lovelace", "SW1A 1AA", expertCosts),
            null,
            null);

    PriorAuthorityResult result = handler.handle(query(), aggregateOf(started(content)));

    assertThat(result.expertDetails().expertFullName()).isEqualTo("Ada Lovelace");
    assertThat(result.expertDetails().expertCosts().timeRequested().minutes()).isEqualTo(30);
    assertThat(result.expertDetails().expertCosts().apportionment().clientShareAmount())
        .isEqualTo(new BigDecimal("104.58"));
    assertThat(result.counselDetails()).isNull();
    assertThat(result.disbursementDetails()).isNull();
  }

  @Test
  void givenDisbursementDetails_whenQueried_thenMapsOnlyDisbursementDetails() {
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            DISBURSEMENT,
            "Disbursement required",
            null,
            null,
            new DisbursementDetails("Travel", BigDecimal.TEN));

    PriorAuthorityResult result = handler.handle(query(), aggregateOf(started(content)));

    assertThat(result.disbursementDetails().disbursementPurpose()).isEqualTo("Travel");
    assertThat(result.expertDetails()).isNull();
    assertThat(result.counselDetails()).isNull();
  }

  @Test
  void givenMissingOrUnstartedAggregate_whenQueried_thenReturnsNull() {
    assertThat(handler.handle(query(), null)).isNull();
    assertThat(handler.handle(query(), aggregateOf())).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"draft", "submitted"})
  void givenErasedContent_whenQueried_thenReturnsNull(String stage) {
    List<Object> events =
        new ArrayList<>(
            List.of(
                new PriorAuthorityDraftStartedEvent(
                    PRIOR_AUTHORITY_ID, APPLICATION_ID, "EXPERT", 1, STARTED_AT, OFFICE_CODE)));
    if ("submitted".equals(stage)) {
      events.add(
          new PriorAuthoritySubmittedEvent(
              PRIOR_AUTHORITY_ID, APPLICATION_ID, "EXPERT", 1, 0L, 0L, SUBMITTED_AT, null));
    }

    assertThat(handler.handle(query(), aggregateOf(events.toArray()))).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {OFFICE_CODE, "OTHER"})
  void givenOfficeScope_whenQueried_thenReturnsOnlyPermittedOffice(String permittedOffice) {
    PriorAuthorityResult result =
        handler.handle(
            new FindPriorAuthorityByPriorAuthorityIdQuery(
                PRIOR_AUTHORITY_ID, new OfficeCodeReadAccessScope(Set.of(permittedOffice))),
            aggregateOf(started(STARTED_CONTENT)));

    assertThat(result != null).isEqualTo(OFFICE_CODE.equals(permittedOffice));
  }

  @Test
  void givenOfficeScopeAndNoOfficeCode_whenQueried_thenReturnsNull() {
    PriorAuthorityAggregate aggregate =
        aggregateOf(
            new PriorAuthorityDraftStartedEvent(
                PRIOR_AUTHORITY_ID,
                APPLICATION_ID,
                "EXPERT",
                1,
                STARTED_AT,
                null,
                Erasable.of(STARTED_CONTENT),
                Erasable.of("{}")));

    assertThat(
            handler.handle(
                new FindPriorAuthorityByPriorAuthorityIdQuery(
                    PRIOR_AUTHORITY_ID, new OfficeCodeReadAccessScope(Set.of(OFFICE_CODE))),
                aggregate))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void givenOwnedDocument_whenDocumentQueried_thenReturnsMetadataTypeAndFilename(
      boolean submitted) {
    UUID documentId = UUID.randomUUID();
    List<Object> events =
        new ArrayList<>(
            List.of(
                started(STARTED_CONTENT),
                uploaded(documentId, "evidence.PDF"),
                new PriorAuthorityDocumentTypeUpdatedEvent(
                    PRIOR_AUTHORITY_ID,
                    documentId,
                    Erasable.of("EXPERT_REPORT"),
                    STARTED_AT.plusSeconds(20),
                    APPLICATION_ID,
                    Erasable.of("{}"))));
    if (submitted) {
      events.add(submitted(STARTED_CONTENT));
    }

    assertThat(
            handler.handle(
                new FindPriorAuthorityDocumentQuery(PRIOR_AUTHORITY_ID, documentId),
                aggregateOf(events.toArray())))
        .isEqualTo(
            new EvidenceDocument(
                documentId,
                "EXPERT_REPORT",
                "evidence.PDF",
                null,
                "application/pdf",
                12L,
                STARTED_AT.plusSeconds(5),
                "CIVIL_APPLY",
                "checksum",
                ".PDF"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing owner", "unknown document", "deleted"})
  void givenUnavailableDocument_whenDocumentQueried_thenReturnsNull(String scenario) {
    UUID documentId = UUID.randomUUID();
    PriorAuthorityAggregate aggregate =
        switch (scenario) {
          case "missing owner" -> null;
          case "unknown document" ->
              aggregateOf(started(STARTED_CONTENT), uploaded(UUID.randomUUID(), "other.pdf"));
          default ->
              aggregateOf(
                  started(STARTED_CONTENT),
                  uploaded(documentId, "evidence.pdf"),
                  new PriorAuthorityDocumentDeletedEvent(
                      PRIOR_AUTHORITY_ID, documentId, STARTED_AT.plusSeconds(20), APPLICATION_ID));
        };

    assertThat(
            handler.handle(
                new FindPriorAuthorityDocumentQuery(PRIOR_AUTHORITY_ID, documentId), aggregate))
        .isNull();
  }

  @Test
  void givenErasedDraftContent_whenDocumentQueried_thenReturnsMetadataWithoutFilename() {
    UUID documentId = UUID.randomUUID();
    PriorAuthorityAggregate aggregate =
        aggregateOf(
            new PriorAuthorityDraftStartedEvent(
                PRIOR_AUTHORITY_ID, APPLICATION_ID, "EXPERT", 1, STARTED_AT, OFFICE_CODE),
            uploaded(documentId, "evidence.pdf"));

    EvidenceDocument document =
        handler.handle(
            new FindPriorAuthorityDocumentQuery(PRIOR_AUTHORITY_ID, documentId), aggregate);

    assertThat(document.documentId()).isEqualTo(documentId);
    assertThat(document.fileName()).isNull();
    assertThat(document.storageFilename()).isEqualTo(documentId + ".PDF");
  }

  private static FindPriorAuthorityByPriorAuthorityIdQuery query() {
    return new FindPriorAuthorityByPriorAuthorityIdQuery(PRIOR_AUTHORITY_ID);
  }

  private static PriorAuthorityDraftStartedEvent started(PriorAuthorityContent content) {
    return new PriorAuthorityDraftStartedEvent(
        PRIOR_AUTHORITY_ID,
        APPLICATION_ID,
        content.priorAuthorityType() == null ? null : content.priorAuthorityType().name(),
        1,
        STARTED_AT,
        OFFICE_CODE,
        Erasable.of(content),
        Erasable.of("{}"));
  }

  private static PriorAuthorityDocumentUploadedEvent uploaded(UUID documentId, String filename) {
    return new PriorAuthorityDocumentUploadedEvent(
        PRIOR_AUTHORITY_ID,
        documentId,
        STARTED_AT.plusSeconds(5),
        12L,
        "application/pdf",
        "checksum",
        APPLICATION_ID,
        "CIVIL_APPLY",
        ".PDF",
        filename);
  }

  private static PriorAuthoritySubmittedEvent submitted(PriorAuthorityContent content) {
    return new PriorAuthoritySubmittedEvent(
        PRIOR_AUTHORITY_ID,
        APPLICATION_ID,
        "EXPERT",
        1,
        0L,
        0L,
        SUBMITTED_AT,
        null,
        Erasable.of(content),
        Erasable.of("{}"));
  }

  private static PriorAuthorityAggregate aggregateOf(Object... events) {
    PriorAuthorityState state = new PriorAuthorityState();
    for (Object event : events) {
      switch (event) {
        case PriorAuthorityDraftStartedEvent e -> PriorAuthorityEvolve.apply(state, e);
        case PriorAuthorityDraftUpdatedEvent e -> PriorAuthorityEvolve.apply(state, e);
        case PriorAuthoritySubmittedEvent e -> PriorAuthorityEvolve.apply(state, e);
        case PriorAuthorityDecisionMadeEvent e -> PriorAuthorityEvolve.apply(state, e);
        case PriorAuthorityDocumentUploadedEvent e -> PriorAuthorityEvolve.apply(state, e);
        case PriorAuthorityDocumentDeletedEvent e -> PriorAuthorityEvolve.apply(state, e);
        case PriorAuthorityDocumentTypeUpdatedEvent e -> PriorAuthorityEvolve.apply(state, e);
        case WorkItemAssigned e -> PriorAuthorityEvolve.apply(state, e);
        default -> throw new IllegalArgumentException(event.toString());
      }
    }
    PriorAuthorityAggregate aggregate = mock(PriorAuthorityAggregate.class);
    when(aggregate.getState()).thenReturn(state);
    return aggregate;
  }
}
