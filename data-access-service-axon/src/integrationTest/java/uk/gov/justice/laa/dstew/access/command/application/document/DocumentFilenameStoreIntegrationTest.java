package uk.gov.justice.laa.dstew.access.command.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.access.BaseIntegrationTest;

class DocumentFilenameStoreIntegrationTest extends BaseIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-09-10T09:00:00Z");

  @Autowired private DocumentFilenameStore store;

  @Test
  void givenDocumentIds_whenFound_thenReturnsOnlyRequestedRowsRegardlessOfDeletionFlag() {
    UUID ownerId = UUID.randomUUID();
    UUID deletedDocumentId = UUID.randomUUID();
    UUID activeDocumentId = UUID.randomUUID();
    UUID unrelatedDocumentId = UUID.randomUUID();
    store.record(ownerId, deletedDocumentId, "deleted.PDF", CREATED_AT);
    store.record(ownerId, activeDocumentId, "active.PDF", CREATED_AT);
    store.record(ownerId, unrelatedDocumentId, "unrelated.PDF", CREATED_AT);
    jdbcTemplate.update(
        "UPDATE axon.document_filename SET deleted_at = now() WHERE document_id = ?",
        deletedDocumentId);

    assertThat(store.findAllByIds(List.of(activeDocumentId, deletedDocumentId, UUID.randomUUID())))
        .containsOnly(
            entry(activeDocumentId, "active.PDF"), entry(deletedDocumentId, "deleted.PDF"));
    assertThat(store.findIncludingDeleted(ownerId, deletedDocumentId)).contains("deleted.PDF");
  }

  @Test
  void givenDocumentOwnedByAnotherOwner_whenLookedUpByOtherOwner_thenNotVisible() {
    UUID ownerId = UUID.randomUUID();
    UUID otherOwnerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    store.record(ownerId, documentId, "evidence.PDF", CREATED_AT);

    assertThat(store.findIncludingDeleted(otherOwnerId, documentId)).isEmpty();
  }

  @Test
  void
      givenApplicationWithPriorAuthority_whenRetentionDeleteRuns_thenRemovesItsDocumentFilenames() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID otherApplicationId = UUID.randomUUID();
    UUID applicationDocumentId = UUID.randomUUID();
    UUID priorAuthorityDocumentId = UUID.randomUUID();
    UUID otherDocumentId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO axon.application_data (application_id, version, payload, payload_hash, created_at)"
            + " VALUES (?, 0, '{}'::jsonb, 'hash', now())",
        applicationId);
    jdbcTemplate.update(
        "INSERT INTO axon.prior_authority_draft"
            + " (prior_authority_id, application_id, payload, payload_hash, created_at, updated_at)"
            + " VALUES (?, ?, '{}'::jsonb, 'hash', now(), now())",
        priorAuthorityId,
        applicationId);
    store.record(applicationId, applicationDocumentId, "application.PDF", CREATED_AT);
    store.record(priorAuthorityId, priorAuthorityDocumentId, "prior.PDF", CREATED_AT);
    store.record(otherApplicationId, otherDocumentId, "other.PDF", CREATED_AT);

    Long deletedApplications =
        jdbcTemplate.queryForObject(
            "SELECT axon.delete_application_data_for_retention(?)", Long.class, applicationId);

    assertThat(deletedApplications).isEqualTo(1L);
    assertThat(store.findAllByIds(List.of(applicationDocumentId, priorAuthorityDocumentId)))
        .isEmpty();
    assertThat(store.findIncludingDeleted(otherApplicationId, otherDocumentId))
        .contains("other.PDF");
  }
}
