package uk.gov.justice.laa.dstew.access.command.application.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApplicationDraftPayloadTest {

  @Test
  void nullMetadataIsNormalisedToEmptyMap() {
    ApplicationDraftPayload payload =
        new ApplicationDraftPayload(
            "APPLICATION_IN_PROGRESS", "LAA-1", Map.of(), "{}", List.of(), Map.of(), null);

    assertThat(payload.metadata()).isEmpty();
  }

  @Test
  void emptyMetadataIsOmittedFromStoredJson() throws Exception {
    ApplicationDraftPayload payload =
        new ApplicationDraftPayload("APPLICATION_IN_PROGRESS", "LAA-1", Map.of(), "{}", List.of());

    String json = JsonMapper.builder().build().writeValueAsString(payload);

    assertThat(json).doesNotContain("\"metadata\"");
  }

  @Test
  void withMetadataReplacesMetadataAndKeepsDocumentFilenames() {
    UUID documentId = UUID.randomUUID();
    ApplicationDraftPayload original =
        new ApplicationDraftPayload(
            "APPLICATION_IN_PROGRESS",
            "LAA-1",
            Map.of(),
            "{}",
            List.of(),
            Map.of(documentId, "evidence.pdf"),
            Map.of("currentStep", "client"));

    ApplicationDraftPayload updated = original.withMetadata(Map.of("currentStep", "proceedings"));

    assertThat(updated.metadata()).containsEntry("currentStep", "proceedings");
    assertThat(updated.documentFilenames()).containsEntry(documentId, "evidence.pdf");
  }

  @Test
  void withDocumentFilenamePreservesMetadata() {
    Map<String, Object> metadata = Map.of("caseworkerId", "CW-9");
    ApplicationDraftPayload original =
        new ApplicationDraftPayload(
            "APPLICATION_IN_PROGRESS", "LAA-1", Map.of(), "{}", List.of(), Map.of(), metadata);
    UUID documentId = UUID.randomUUID();

    ApplicationDraftPayload updated = original.withDocumentFilename(documentId, "evidence.pdf");

    assertThat(updated.metadata()).isEqualTo(metadata);
    assertThat(updated.documentFilenames()).containsEntry(documentId, "evidence.pdf");
  }
}
