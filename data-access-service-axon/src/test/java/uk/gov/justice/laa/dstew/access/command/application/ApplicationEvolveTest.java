package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

/** Unit tests for {@link ApplicationEvolve} decision-fold behaviour. */
class ApplicationEvolveTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = 3L)
  void givenDocumentUploadVersion_whenApplied_thenAdvancesOnlyReferencedDataVersion(Long version) {
    ApplicationState state = new ApplicationState();
    state.applicationDataVersion = 2L;
    state.applicationVersion = 7L;
    ApplicationEvolve.apply(
        state,
        new ApplicationDocumentUploadedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "INVOICE",
            Instant.now(),
            12L,
            "application/pdf",
            "checksum",
            "CIVIL_APPLY",
            version));

    assertThat(state.applicationDataVersion).isEqualTo(version == null ? 2L : version);
    assertThat(state.applicationVersion).isEqualTo(7L);
    assertThat(state.uploadedDocuments.getFirst().deleted()).isFalse();
  }

  @Test
  void givenLegacySerializedUpload_whenDeserialized_thenPreservesDataVersion() throws Exception {
    String json =
        """
        {"applicationId":"00000000-0000-0000-0000-000000000001",
         "documentId":"00000000-0000-0000-0000-000000000002",
         "documentType":"INVOICE","uploadedAt":"2026-09-28T10:00:00Z",
         "size":12,"contentType":"application/pdf","checksum":"checksum",
         "sourceService":"CIVIL_APPLY"}
        """;
    ApplicationDocumentUploadedEvent event =
        new ObjectMapper()
            .findAndRegisterModules()
            .readValue(json, ApplicationDocumentUploadedEvent.class);
    ApplicationState state = new ApplicationState();
    state.applicationDataVersion = 2L;

    ApplicationEvolve.apply(state, event);

    assertThat(event.applicationDataVersion()).isNull();
    assertThat(state.applicationDataVersion).isEqualTo(2L);
    assertThat(state.uploadedDocuments).hasSize(1);
    assertThat(state.uploadedDocuments.getFirst().documentType()).isEqualTo("INVOICE");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenHistoricalMetadataJson_whenReadAsSharedRecord_thenPreservesStoredShape(
      boolean deleted) {
    String json =
        """
        {"documentId":"00000000-0000-0000-0000-000000000002",
         "documentType":"INVOICE","uploadedAt":"2026-09-28T10:00:00Z",
         "size":12,"contentType":"application/pdf","checksum":"checksum",
         "sourceService":"CIVIL_APPLY","deleted":%s}
        """
            .formatted(deleted);
    var objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();

    DocumentMetadata document = objectMapper.readValue(json, DocumentMetadata.class);

    assertThat(document.documentType()).isEqualTo("INVOICE");
    assertThat(document.deleted()).isEqualTo(deleted);
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(document)))
        .isEqualTo(objectMapper.readTree(json));
  }

  @Test
  void givenDecisionMadeEventWithGranted_whenApply_thenSetsOverallDecision() {
    ApplicationState state = new ApplicationState();
    UUID applicationId = UUID.randomUUID();
    state.applicationId = applicationId;
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(
            applicationId, 1L, 1L, "GRANTED", AutoGrantedState.AUTOGRANTED, Instant.now());

    ApplicationEvolve.apply(state, event);

    assertThat(state.overallDecision).isEqualTo("GRANTED");
  }

  @Test
  void givenDecisionMadeEventWithRefused_whenApply_thenSetsOverallDecision() {
    ApplicationState state = new ApplicationState();
    UUID applicationId = UUID.randomUUID();
    state.applicationId = applicationId;
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(
            applicationId, 1L, 1L, "REFUSED", AutoGrantedState.MANUAL, Instant.now());

    ApplicationEvolve.apply(state, event);

    assertThat(state.overallDecision).isEqualTo("REFUSED");
  }

  @ParameterizedTest
  @CsvSource({"GRANTED,APPLICATION_GRANTED", "REFUSED,APPLICATION_REFUSED"})
  void givenDecisionEvent_whenApplied_thenUpdatesApplicationStatus(
      String overallDecision, String expectedStatus) {
    ApplicationState state = new ApplicationState();

    ApplicationEvolve.apply(
        state,
        new ApplicationDecisionMadeEvent(
            UUID.randomUUID(), 2L, 2L, overallDecision, AutoGrantedState.MANUAL, Instant.now()));

    assertThat(state.status).isEqualTo(expectedStatus);
  }

  @Test
  void givenDecisionEventWithoutOverallDecision_whenApplied_thenRetainsCurrentApplicationStatus() {
    ApplicationState state = new ApplicationState();
    state.status = ApplicationStatus.APPLICATION_SUBMITTED.getValue();

    ApplicationEvolve.apply(
        state,
        new ApplicationDecisionMadeEvent(
            UUID.randomUUID(), 2L, 2L, null, AutoGrantedState.MANUAL, Instant.now()));

    assertThat(state.status).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.getValue());
  }

  @Test
  void givenDocumentUploadEvents_whenApplied_thenAppendsMetadataInUploadOrder() {
    ApplicationState state = new ApplicationState();
    UUID applicationId = UUID.randomUUID();
    UUID firstDocumentId = UUID.randomUUID();
    UUID secondDocumentId = UUID.randomUUID();

    ApplicationEvolve.apply(
        state,
        new ApplicationDocumentUploadedEvent(
            applicationId,
            firstDocumentId,
            "GATEWAY_EVIDENCE",
            Instant.parse("2026-09-28T10:00:00Z"),
            12L,
            "application/pdf",
            "first-checksum",
            "CIVIL_APPLY"));
    ApplicationEvolve.apply(
        state,
        new ApplicationDocumentUploadedEvent(
            applicationId,
            secondDocumentId,
            "INVOICE",
            Instant.parse("2026-09-28T10:01:00Z"),
            13L,
            "application/pdf",
            "second-checksum",
            "CIVIL_APPLY"));

    assertThat(state.uploadedDocuments)
        .extracting(DocumentMetadata::documentId)
        .containsExactly(firstDocumentId, secondDocumentId);
    assertThat(state.uploadedDocuments.getFirst().documentType()).isEqualTo("GATEWAY_EVIDENCE");
  }
}
