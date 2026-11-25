package uk.gov.justice.laa.dstew.access.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;

class DocumentUploadEventCompatibilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void givenHistoricalApplicationUpload_whenDeserialised_thenSuffixIsUnknown() {
    String json =
        """
        {"applicationId":"00000000-0000-0000-0000-000000000001",
         "documentId":"00000000-0000-0000-0000-000000000002",
         "documentType":"GATEWAY_EVIDENCE","uploadedAt":"2026-09-01T10:00:00Z",
         "size":12,"contentType":"application/pdf","checksum":"checksum",
         "sourceService":"CIVIL_APPLY","applicationDataVersion":3}
        """;
    var event = objectMapper.readValue(json, ApplicationDocumentUploadedEvent.class);
    assertThat(event.fileSuffix()).isNull();
    assertThat(event.applicationDataVersion()).isEqualTo(3L);
    assertThat(event.documentType()).isEqualTo("GATEWAY_EVIDENCE");
  }

  @Test
  void givenHistoricalPriorAuthorityUpload_whenDeserialised_thenSuffixIsUnknown() {
    String json =
        """
        {"priorAuthorityId":"00000000-0000-0000-0000-000000000001",
         "documentId":"00000000-0000-0000-0000-000000000002",
         "uploadedAt":"2026-09-01T10:00:00Z","size":12,"contentType":"application/pdf",
         "checksum":"checksum","parentApplicationId":"00000000-0000-0000-0000-000000000003",
         "sourceService":"CIVIL_APPLY"}
        """;
    var event = objectMapper.readValue(json, PriorAuthorityDocumentUploadedEvent.class);
    assertThat(event.fileSuffix()).isNull();
    assertThat(event.parentApplicationId())
        .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000003"));
  }

  @Test
  void givenHistoricalMetadata_whenDeserialised_thenSuffixIsUnknown() {
    var metadata =
        objectMapper.readValue(
            """
        {"documentId":"00000000-0000-0000-0000-000000000002",
         "documentType":null,"uploadedAt":"2026-09-01T10:00:00Z","size":12,
         "contentType":"application/pdf","checksum":null,"sourceService":"CIVIL_APPLY","deleted":false}
        """,
            DocumentMetadata.class);
    assertThat(metadata.fileSuffix()).isNull();
    assertThat(metadata.deleted()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {".pdf", ".PDF", ".png", ""})
  void givenNewUploadEvents_whenRoundTripped_thenPreservesExactSuffix(String suffix) {
    UUID ownerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant timestamp = Instant.parse("2026-09-01T10:00:00Z");
    var application =
        new ApplicationDocumentUploadedEvent(
            ownerId,
            documentId,
            null,
            timestamp,
            12L,
            "application/pdf",
            null,
            "CIVIL_APPLY",
            null,
            suffix);
    var priorAuthority =
        new PriorAuthorityDocumentUploadedEvent(
            ownerId,
            documentId,
            timestamp,
            12L,
            "application/pdf",
            null,
            UUID.randomUUID(),
            "CIVIL_APPLY",
            suffix);
    assertThat(
            objectMapper.readValue(
                objectMapper.writeValueAsString(application),
                ApplicationDocumentUploadedEvent.class))
        .isEqualTo(application);
    assertThat(
            objectMapper.readValue(
                objectMapper.writeValueAsString(priorAuthority),
                PriorAuthorityDocumentUploadedEvent.class))
        .isEqualTo(priorAuthority);
  }
}
