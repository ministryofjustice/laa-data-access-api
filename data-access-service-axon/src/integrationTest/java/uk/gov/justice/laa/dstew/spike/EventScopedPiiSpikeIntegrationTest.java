package uk.gov.justice.laa.dstew.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.Body;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.CompactDraftContent;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.Content;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.DeclarationRequired;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.DraftCandidate;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.DraftHistoryCompacted;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.ForcedRollback;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.PrepareDeclaration;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.Projection;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.Rename;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.Review;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.ReviewedContentChanged;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.ReviewedDraft;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.StartDraft;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.Store;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.Submit;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.SubmitReviewed;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.UpdateDraft;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.Upload;
import uk.gov.justice.laa.dstew.spike.EventScopedPiiSpike.WriteProbe;

/** Executable evidence for the event-content spike, not a production migration. */
@Testcontainers
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({EventScopedPiiSpike.Config.class, TestJwtDecoderConfig.class})
@Sql(
    scripts = "/spike/event-scoped-pii.sql",
    config = @SqlConfig(separator = ";;"),
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventScopedPiiSpikeIntegrationTest {

  // Synthetic, deliberately incompressible draft body: about 16 KB of hex per revision.
  private static final int BODY_RANDOM_BYTES = 8 * 1024;
  private static final int DRAFT_EDITS = 40;

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private CommandGateway commands;
  @Autowired private RetryingCommandDispatcher dispatcher;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper mapper;
  @Autowired private Store store;
  @Autowired private Projection projection;
  @Autowired private WriteProbe probe;
  @Autowired private AxonConfiguration axon;
  @Autowired private TransactionTemplate transactions;

  @AfterEach
  void resetProbe() {
    probe.reset();
  }

  @Test
  void givenSidecars_whenProjectingAndRebuilding_thenPersistsOneCorrectCurrentState() {
    UUID id = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    processor().shutdown().join();
    try {
      commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
      commands.sendAndWait(new Upload(id, 0, documentId, "EVIDENCE", "synthetic-original.pdf"));
      commands.sendAndWait(new Rename(id, 1, documentId, "synthetic-revised.pdf"));
      DraftCandidate candidate = prepare(id, 2);
      commands.sendAndWait(new Submit(id, candidate.streamVersion()));
      commands.sendAndWait(new Review(id, 4));

      var facts = store.facts(id, 5);
      assertThat(facts).hasSize(6);
      var uploadedName =
          store.eventContent(facts.get(1).eventId(), EventScopedPiiSpike.Filename.class);
      var revisedName =
          store.eventContent(facts.get(2).eventId(), EventScopedPiiSpike.Filename.class);
      assertThat(uploadedName.fileName()).isEqualTo("synthetic-original.pdf");
      assertThat(revisedName.fileName()).isEqualTo("synthetic-revised.pdf");
      assertThat(
              mapper
                  .readTree(
                      jdbc.queryForObject(
                          "SELECT payload::text FROM spike.event_content WHERE event_id = ?",
                          String.class,
                          facts.get(2).eventId()))
                  .size())
          .isEqualTo(1);
      assertThat(store.eventContent(facts.get(4).eventId(), Content.class).documentFilenames())
          .containsEntry(documentId, "synthetic-revised.pdf");
      assertThat(contentCount(facts.get(5).eventId())).isZero();
      assertThat(store.domainState(id, 5).reviewed()).isTrue();
      assertThat(mapper.writeValueAsString(store.domainState(id, 5)))
          .doesNotContain("Synthetic applicant", "synthetic-original.pdf", "synthetic-revised.pdf");
      assertThat(eventJson(id))
          .allSatisfy(json -> assertThat(json).doesNotContain("Synthetic applicant", "synthetic-"));

      projection.project(facts.getFirst().fact());
      assertThat(store.view(id).state().version()).isZero();
      assertThat(store.view(id).state().submitted()).isFalse();
      assertThat(store.view(id).content().documentFilenames()).isEmpty();
      projection.project(facts.get(1).fact());
      assertThat(store.view(id).content().documentFilenames())
          .containsEntry(documentId, "synthetic-original.pdf");
    } finally {
      processor().start().join();
    }

    awaitVersion(id, 5);
    var before = store.view(id);
    assertThat(before.state().submitted()).isTrue();
    assertThat(before.state().reviewed()).isTrue();
    assertThat(before.content().documentFilenames())
        .containsExactlyEntriesOf(Map.of(documentId, "synthetic-revised.pdf"));

    processor().shutdown().join();
    try {
      var first = store.facts(id, 5).getFirst();
      projection.project(first.fact());
      assertThat(store.view(id)).isEqualTo(before);
      processor().resetTokens().join();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM spike.application_view WHERE application_id = ?",
                  Integer.class,
                  id))
          .isZero();
      assertThat(contentCount(first.eventId())).isEqualTo(1);
    } finally {
      processor().start().join();
    }
    awaitVersion(id, 5);
    assertThat(store.view(id)).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM spike.application_view WHERE application_id = ?",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  @Test
  void givenMultipleDocuments_whenRenamedById_thenOnlyTargetFilenameChanges() {
    UUID id = UUID.randomUUID();
    final UUID firstDocumentId = UUID.randomUUID();
    final UUID secondDocumentId = UUID.randomUUID();
    commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
    commands.sendAndWait(new Upload(id, 0, firstDocumentId, "EVIDENCE", "synthetic-first.pdf"));
    commands.sendAndWait(
        new Upload(id, 1, secondDocumentId, "COURT_ORDER", "synthetic-second.pdf"));
    commands.sendAndWait(new Rename(id, 2, firstDocumentId, "synthetic-first-revised.pdf"));

    assertThat(store.reconstruct(id, 3).content().documentFilenames())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                firstDocumentId, "synthetic-first-revised.pdf",
                secondDocumentId, "synthetic-second.pdf"));
    assertThat(store.reconstruct(id, 2).content().documentFilenames())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                firstDocumentId, "synthetic-first.pdf",
                secondDocumentId, "synthetic-second.pdf"));

    commands.sendAndWait(new Rename(id, 3, secondDocumentId, "synthetic-second-revised.pdf"));
    commands.sendAndWait(new Rename(id, 4, firstDocumentId, "synthetic-first-final.pdf"));
    DraftCandidate candidate = prepare(id, 5);
    commands.sendAndWait(new Submit(id, candidate.streamVersion()));

    var expectedFilenames =
        Map.of(
            firstDocumentId, "synthetic-first-final.pdf",
            secondDocumentId, "synthetic-second-revised.pdf");
    var submitted = store.facts(id, 7).getLast();
    assertThat(store.eventContent(submitted.eventId(), Content.class).documentFilenames())
        .containsExactlyInAnyOrderEntriesOf(expectedFilenames);
    awaitVersion(id, 7);
    processor().shutdown().join();
    try {
      processor().resetTokens().join();
    } finally {
      processor().start().join();
    }
    awaitVersion(id, 7);
    assertThat(store.view(id).content().documentFilenames())
        .containsExactlyInAnyOrderEntriesOf(expectedFilenames);
    assertThat(store.view(id).state().documentTypes())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(firstDocumentId, "EVIDENCE", secondDocumentId, "COURT_ORDER"));
  }

  @Test
  void givenConcurrentUploadsAtOneVersion_whenAppending_thenOneWinsWithNoOrphanSidecar()
      throws Exception {
    UUID id = UUID.randomUUID();
    commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
    probe.applicationId = id;
    probe.barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Callable<Boolean>> attempts =
          List.of(
              () -> tryCommand(new Upload(id, 0, UUID.randomUUID(), "EVIDENCE", "synthetic-a.pdf")),
              () ->
                  tryCommand(new Upload(id, 0, UUID.randomUUID(), "EVIDENCE", "synthetic-b.pdf")));
      var results = executor.invokeAll(attempts, 20, TimeUnit.SECONDS);
      assertThat(results)
          .extracting(result -> result.get(1, TimeUnit.SECONDS))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      probe.reset();
    }
    var facts = store.facts(id, 1);
    assertThat(facts).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM spike.event_content p "
                    + "LEFT JOIN axon.domain_event_entry e ON e.event_identifier = p.event_id "
                    + "WHERE e.event_identifier IS NULL",
                Integer.class))
        .isZero();
    assertThat(store.domainState(id, 1).documentTypes()).hasSize(1);
    awaitVersion(id, 1);
    assertThat(store.view(id).content().documentFilenames()).hasSize(1);
  }

  @Test
  void givenUploadRacingSubmit_whenSealing_thenNoAcceptedFilenameIsLost() throws Exception {
    UUID id = UUID.randomUUID();
    final UUID documentId = UUID.randomUUID();
    commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
    DraftCandidate candidate = prepare(id, 0);
    probe.applicationId = id;
    probe.barrier = new CyclicBarrier(2);
    boolean uploadAccepted;
    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Callable<Boolean>> attempts =
          List.of(
              () ->
                  tryCommand(
                      new Upload(
                          id,
                          candidate.streamVersion(),
                          documentId,
                          "EVIDENCE",
                          "synthetic-racing.pdf")),
              () -> tryCommand(new Submit(id, candidate.streamVersion())));
      var results = executor.invokeAll(attempts, 20, TimeUnit.SECONDS);
      uploadAccepted = results.getFirst().get(1, TimeUnit.SECONDS);
      assertThat(results.getLast().get(1, TimeUnit.SECONDS)).isNotEqualTo(uploadAccepted);
    } finally {
      probe.reset();
    }

    if (uploadAccepted) {
      assertThatThrownBy(() -> commands.sendAndWait(new Submit(id, candidate.streamVersion())))
          .isInstanceOf(EventScopedPiiSpike.StaleStreamVersion.class);
      DraftCandidate updated = prepare(id, 2);
      commands.sendAndWait(new Submit(id, updated.streamVersion()));
    }
    long submittedVersion = uploadAccepted ? 4 : 2;
    var submitted = store.facts(id, submittedVersion).getLast();
    var sealed = store.eventContent(submitted.eventId(), Content.class);
    assertThat(sealed.documentFilenames())
        .containsExactlyInAnyOrderEntriesOf(
            uploadAccepted ? Map.of(documentId, "synthetic-racing.pdf") : Map.of());
    awaitVersion(id, submittedVersion);
    assertThat(store.view(id).content()).isEqualTo(sealed);
  }

  @Test
  void givenDeclaredContent_whenDraftChangesDuringSubmit_thenRetryRequiresFreshDeclaration()
      throws Exception {
    UUID id = UUID.randomUUID();
    final UUID firstDocumentId = UUID.randomUUID();
    final UUID secondDocumentId = UUID.randomUUID();
    processor().shutdown().join();
    try {
      commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
      assertThatThrownBy(() -> store.getDraft(id)).isInstanceOf(DeclarationRequired.class);
      assertThatThrownBy(() -> commands.sendAndWait(new Submit(id, 0)))
          .isInstanceOf(DeclarationRequired.class);
      commands.sendAndWait(
          new Upload(id, 0, firstDocumentId, "EVIDENCE", "synthetic-declared.pdf"));
      DraftCandidate declared = prepare(id, 1);
      String originalJson = contentJson(declared.eventId());
      assertThat(store.getDraft(id)).isEqualTo(declared);
      assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(3);
      assertThat(applicationContentCount(id)).isEqualTo(3);
      assertThat(declared.content().documentFilenames())
          .containsExactlyInAnyOrderEntriesOf(Map.of(firstDocumentId, "synthetic-declared.pdf"));

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE spike.event_content SET payload = "
                          + "payload || '{\"applicantName\":\"Synthetic replacement\"}'::jsonb "
                          + "WHERE event_id = ?",
                      declared.eventId()))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("Event content is immutable");
      assertThat(contentJson(declared.eventId())).isEqualTo(originalJson);

      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      probe.applicationId = id;
      probe.submissionEntered = entered;
      probe.releaseSubmission = release;
      try (var executor = Executors.newSingleThreadExecutor()) {
        var attempt =
            executor.submit(() -> dispatcher.dispatch(new Submit(id, declared.streamVersion())));
        try {
          assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
          commands.sendAndWait(
              new Upload(
                  id,
                  declared.streamVersion(),
                  secondDocumentId,
                  "COURT_ORDER",
                  "synthetic-later.pdf"));
        } finally {
          release.countDown();
        }
        assertThatThrownBy(() -> attempt.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(EventScopedPiiSpike.StaleStreamVersion.class);
      } finally {
        probe.reset();
      }

      assertThat(store.domainState(id, 3).submitted()).isFalse();
      assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(4);
      assertThat(applicationContentCount(id)).isEqualTo(4);
      assertThat(contentJson(declared.eventId())).isEqualTo(originalJson);
      assertThatThrownBy(() -> store.getDraft(id)).isInstanceOf(DeclarationRequired.class);
      commands.sendAndWait(new Rename(id, 3, firstDocumentId, "synthetic-declared-revised.pdf"));
      DraftCandidate updated = prepare(id, 4);
      assertThat(updated.content().documentFilenames())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  firstDocumentId, "synthetic-declared-revised.pdf",
                  secondDocumentId, "synthetic-later.pdf"));
      commands.sendAndWait(new Submit(id, updated.streamVersion()));
      var submitted = store.facts(id, 6).getLast();
      assertThat(contentJson(submitted.eventId())).isEqualTo(contentJson(updated.eventId()));
      assertThat(contentJson(declared.eventId())).isEqualTo(originalJson);
      assertThatThrownBy(() -> store.getDraft(id)).hasMessageContaining("already submitted");
    } finally {
      processor().start().join();
    }
    awaitVersion(id, 6);
  }

  @Test
  void givenCompactedDraftContent_whenProjectionLagsOrRebuilds_thenUsesSubmittedCheckpoint() {
    UUID id = UUID.randomUUID();
    final UUID firstDocumentId = UUID.randomUUID();
    final UUID secondDocumentId = UUID.randomUUID();
    processor().shutdown().join();
    Content submittedContent;
    try {
      commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
      commands.sendAndWait(
          new Upload(id, 0, firstDocumentId, "EVIDENCE", "synthetic-before-compaction.pdf"));
      commands.sendAndWait(
          new Upload(id, 1, secondDocumentId, "COURT_ORDER", "synthetic-preserved.pdf"));
      commands.sendAndWait(new Rename(id, 2, firstDocumentId, "synthetic-at-submission.pdf"));
      DraftCandidate candidate = prepare(id, 3);
      submittedContent = candidate.content();
      commands.sendAndWait(new Submit(id, candidate.streamVersion()));
      commands.sendAndWait(new Review(id, 5));
      commands.sendAndWait(new CompactDraftContent(id, 6));

      var facts = store.facts(id, Long.MAX_VALUE);
      assertThat(facts).hasSize(8);
      assertThat(applicationContentCount(id)).isEqualTo(1);
      assertThat(contentCount(candidate.eventId())).isZero();
      assertThat(contentCount(facts.get(5).eventId())).isEqualTo(1);
      assertThat(store.domainState(id, 7).draftContentCompacted()).isTrue();
      assertThat(store.domainState(id, 4).documentTypes())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(firstDocumentId, "EVIDENCE", secondDocumentId, "COURT_ORDER"));
      assertThatThrownBy(() -> store.reconstruct(id, 4)).isInstanceOf(DraftHistoryCompacted.class);
      assertThat(store.reconstruct(id, 5).content()).isEqualTo(submittedContent);
      projection.project(facts.getFirst().fact());
      assertThat(store.view(id).state().version()).isEqualTo(5);
      assertThat(store.view(id).state().submitted()).isTrue();
      assertThat(store.view(id).state().reviewed()).isFalse();
      assertThat(store.view(id).content()).isEqualTo(submittedContent);

      transactions.executeWithoutResult(
          status -> {
            jdbc.update(
                "DELETE FROM spike.event_content WHERE event_id = ?", facts.get(5).eventId());
            assertThatThrownBy(() -> store.reconstruct(id, 7))
                .hasMessageContaining("content missing");
            assertThat(store.domainState(id, 7).reviewed()).isTrue();
            status.setRollbackOnly();
          });
    } finally {
      processor().start().join();
    }
    awaitVersion(id, 7);
    var before = store.view(id);
    assertThat(before.content()).isEqualTo(submittedContent);
    assertThat(before.state().reviewed()).isTrue();

    processor().shutdown().join();
    try {
      processor().resetTokens().join();
    } finally {
      processor().start().join();
    }
    awaitVersion(id, 7);
    assertThat(store.view(id)).isEqualTo(before);
    assertThat(applicationContentCount(id)).isEqualTo(1);
    assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(8);
  }

  @Test
  void givenCompactionFailure_whenAppendRollsBack_thenDraftContentAndHistoryArePreserved() {
    UUID id = UUID.randomUUID();
    commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
    DraftCandidate candidate = prepare(id, 0);
    commands.sendAndWait(new Submit(id, candidate.streamVersion()));
    assertThat(applicationContentCount(id)).isEqualTo(3);
    probe.applicationId = id;
    probe.failAfterAppend = true;
    try {
      assertThatThrownBy(() -> commands.sendAndWait(new CompactDraftContent(id, 2)))
          .isInstanceOf(ForcedRollback.class);
    } finally {
      probe.reset();
    }
    assertThat(applicationContentCount(id)).isEqualTo(3);
    assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(3);
    assertThat(store.domainState(id, 2).draftContentCompacted()).isFalse();
    assertThat(store.reconstruct(id, 0).content()).isEqualTo(content());
    commands.sendAndWait(new CompactDraftContent(id, 2));
    assertThat(applicationContentCount(id)).isEqualTo(1);
    assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(4);
    awaitVersion(id, 3);
  }

  @Test
  void givenHashBoundReview_whenDraftChangesDuringSubmit_thenRetryRejectsWithoutPrepareWrite()
      throws Exception {
    UUID id = UUID.randomUUID();
    final UUID firstDocumentId = UUID.randomUUID();
    final UUID secondDocumentId = UUID.randomUUID();
    processor().shutdown().join();
    ReviewedDraft updated;
    try {
      commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
      commands.sendAndWait(new Upload(id, 0, firstDocumentId, "EVIDENCE", "synthetic-one.pdf"));
      commands.sendAndWait(new UpdateDraft(id, 1, body(1)));
      ReviewedDraft reviewed = store.review(id);
      assertThat(store.review(id)).isEqualTo(reviewed);
      assertThat(reviewed.streamVersion()).isEqualTo(2);
      assertThat(reviewed.content().details()).isEqualTo(body(1).details());
      assertThat(reviewed.content().documentFilenames())
          .containsExactlyEntriesOf(Map.of(firstDocumentId, "synthetic-one.pdf"));
      assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(3);
      assertThat(applicationContentCount(id)).isEqualTo(3);

      assertThatThrownBy(() -> commands.sendAndWait(new SubmitReviewed(id, 2, "0".repeat(64))))
          .isInstanceOf(ReviewedContentChanged.class);
      assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(3);

      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      probe.applicationId = id;
      probe.submissionEntered = entered;
      probe.releaseSubmission = release;
      try (var executor = Executors.newSingleThreadExecutor()) {
        var attempt =
            executor.submit(
                () ->
                    dispatcher.dispatch(
                        new SubmitReviewed(id, reviewed.streamVersion(), reviewed.contentHash())));
        try {
          assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
          commands.sendAndWait(
              new Upload(id, 2, secondDocumentId, "COURT_ORDER", "synthetic-two.pdf"));
        } finally {
          release.countDown();
        }
        assertThatThrownBy(() -> attempt.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(EventScopedPiiSpike.StaleStreamVersion.class);
      } finally {
        probe.reset();
      }

      assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(4);
      assertThat(store.domainState(id, 3).submitted()).isFalse();
      updated = store.review(id);
      assertThat(updated.streamVersion()).isEqualTo(3);
      assertThat(updated.contentHash()).isNotEqualTo(reviewed.contentHash());
      assertThatThrownBy(
              () -> commands.sendAndWait(new SubmitReviewed(id, 3, reviewed.contentHash())))
          .isInstanceOf(ReviewedContentChanged.class);

      commands.sendAndWait(new SubmitReviewed(id, updated.streamVersion(), updated.contentHash()));
      var submitted = store.facts(id, 4).getLast();
      Content stored = store.eventContent(submitted.eventId(), Content.class);
      assertThat(stored).isEqualTo(updated.content());
      assertThat(store.contentHash(stored)).isEqualTo(updated.contentHash());
    } finally {
      processor().start().join();
    }
    awaitVersion(id, 4);
    assertThat(store.view(id).content()).isEqualTo(updated.content());
  }

  @Test
  void givenRepeatedFullBodyEdits_whenSubmittedAndCompacted_thenOnlySubmittedBodyRemains() {
    UUID id = UUID.randomUUID();
    commands.sendAndWait(
        new StartDraft(
            id,
            "SYNTHETIC_OFFICE",
            new Content(body(0).applicantName(), body(0).details(), Map.of())));
    for (int revision = 1; revision <= DRAFT_EDITS; revision++) {
      commands.sendAndWait(new UpdateDraft(id, revision - 1, body(revision)));
    }
    final ContentMetrics drafting = metrics(id);
    ReviewedDraft reviewed = store.review(id);
    commands.sendAndWait(new SubmitReviewed(id, reviewed.streamVersion(), reviewed.contentHash()));
    final ContentMetrics submitted = metrics(id);
    commands.sendAndWait(new CompactDraftContent(id, DRAFT_EDITS + 1));
    final ContentMetrics compacted = metrics(id);

    System.out.printf(
        "SPIKE-METRIC edits=%d bodyJsonBytes~%d drafting=%s submitted=%s compacted=%s%n",
        DRAFT_EDITS, compacted.jsonBytes(), drafting, submitted, compacted);
    assertThat(drafting.rows()).isEqualTo(DRAFT_EDITS + 1);
    assertThat(submitted.rows()).isEqualTo(DRAFT_EDITS + 2);
    assertThat(compacted.rows()).isEqualTo(1);
    assertThat(drafting.jsonBytes()).isGreaterThan(DRAFT_EDITS * compacted.jsonBytes() * 9 / 10);
    assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(DRAFT_EDITS + 3);
    assertThat(store.reconstruct(id, DRAFT_EDITS + 1).content()).isEqualTo(reviewed.content());
    awaitVersion(id, DRAFT_EDITS + 2);
    assertThat(store.view(id).content()).isEqualTo(reviewed.content());
  }

  @Test
  void givenFailureAfterAppend_whenCommandRollsBack_thenNeitherEventNorSidecarCommits() {
    UUID id = UUID.randomUUID();
    commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
    final int before =
        jdbc.queryForObject("SELECT COUNT(*) FROM spike.event_content", Integer.class);
    probe.applicationId = id;
    probe.failAfterAppend = true;
    assertThatThrownBy(
            () ->
                commands.sendAndWait(
                    new Upload(id, 0, UUID.randomUUID(), "EVIDENCE", "synthetic-rollback.pdf")))
        .isInstanceOf(ForcedRollback.class);
    probe.reset();
    assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spike.event_content", Integer.class))
        .isEqualTo(before);
    commands.sendAndWait(new Upload(id, 0, UUID.randomUUID(), "EVIDENCE", "synthetic-retry.pdf"));
    assertThat(store.facts(id, Long.MAX_VALUE)).hasSize(2);
  }

  @Test
  void givenErasedOrMissingSidecar_whenReconstructing_thenFailsExplicitlyButDomainReplayWorks() {
    UUID id = UUID.randomUUID();
    commands.sendAndWait(new StartDraft(id, "SYNTHETIC_OFFICE", content()));
    awaitVersion(id, 0);
    String eventId = store.facts(id, 0).getFirst().eventId();
    processor().shutdown().join();
    try {
      transactions.executeWithoutResult(
          status -> {
            jdbc.update(
                "UPDATE spike.event_content SET payload = NULL, erased_at = CURRENT_TIMESTAMP "
                    + "WHERE event_id = ?",
                eventId);
            assertThatThrownBy(() -> store.reconstruct(id, 0))
                .hasMessageContaining("intentionally erased");
            assertThat(store.domainState(id, 0).officeCode()).isEqualTo("SYNTHETIC_OFFICE");
            jdbc.update("DELETE FROM spike.event_content WHERE event_id = ?", eventId);
            assertThatThrownBy(() -> store.reconstruct(id, 0))
                .hasMessageContaining("content missing");
            status.setRollbackOnly();
          });
    } finally {
      processor().start().join();
    }
    assertThat(store.reconstruct(id, 0).content()).isEqualTo(content());
  }

  private boolean tryCommand(Object command) {
    try {
      commands.sendAndWait(command);
      return true;
    } catch (RuntimeException exception) {
      assertThat(exception).hasStackTraceContaining("AppendEventsTransactionRejectedException");
      return false;
    }
  }

  private Content content() {
    return new Content("Synthetic applicant", "Synthetic details", Map.of());
  }

  private Body body(int revision) {
    var bytes = new byte[BODY_RANDOM_BYTES];
    new Random(revision).nextBytes(bytes);
    return new Body("Synthetic applicant " + revision, HexFormat.of().formatHex(bytes));
  }

  private ContentMetrics metrics(UUID id) {
    return jdbc.queryForObject(
        "SELECT COUNT(c.event_id) AS content_rows, "
            + "COALESCE(SUM(octet_length(c.payload::text)), 0) AS json_bytes, "
            + "COALESCE(SUM(pg_column_size(c.payload)), 0) AS stored_bytes, "
            + "(SELECT SUM(octet_length(payload)) FROM axon.domain_event_entry "
            + "WHERE aggregate_identifier = ?) AS event_bytes "
            + "FROM axon.domain_event_entry e "
            + "JOIN spike.event_content c ON c.event_id = e.event_identifier "
            + "WHERE e.aggregate_identifier = ?",
        (row, index) ->
            new ContentMetrics(
                row.getLong("content_rows"),
                row.getLong("json_bytes"),
                row.getLong("stored_bytes"),
                row.getLong("event_bytes")),
        id.toString(),
        id.toString());
  }

  private record ContentMetrics(long rows, long jsonBytes, long storedBytes, long eventBytes) {}

  private StreamingEventProcessor processor() {
    var processor =
        axon.getComponents(StreamingEventProcessor.class).get(EventScopedPiiSpike.PROCESSOR);
    assertThat(processor).as("Spike streaming processor").isNotNull();
    return processor;
  }

  private void awaitVersion(UUID id, long version) {
    await()
        .atMost(Duration.ofSeconds(15))
        .ignoreException(org.springframework.dao.EmptyResultDataAccessException.class)
        .untilAsserted(() -> assertThat(store.view(id).state().version()).isEqualTo(version));
  }

  private DraftCandidate prepare(UUID id, long expectedVersion) {
    commands.sendAndWait(new PrepareDeclaration(id, expectedVersion));
    return store.getDraft(id);
  }

  private int contentCount(String eventId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM spike.event_content WHERE event_id = ?", Integer.class, eventId);
  }

  private int applicationContentCount(UUID id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM spike.event_content c "
            + "JOIN axon.domain_event_entry e ON e.event_identifier = c.event_id "
            + "WHERE e.aggregate_identifier = ?",
        Integer.class,
        id.toString());
  }

  private String contentJson(String eventId) {
    return jdbc.queryForObject(
        "SELECT payload::text FROM spike.event_content WHERE event_id = ?", String.class, eventId);
  }

  private List<String> eventJson(UUID id) {
    return jdbc.query(
        "SELECT payload FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
        (row, index) -> new String(row.getBytes("payload"), StandardCharsets.UTF_8),
        id.toString());
  }
}
