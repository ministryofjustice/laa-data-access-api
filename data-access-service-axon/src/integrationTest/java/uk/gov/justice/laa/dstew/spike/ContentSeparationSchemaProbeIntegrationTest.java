package uk.gov.justice.laa.dstew.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.AddProbeNote;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ContentReaderProjection;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ContentReaders;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.Erasable;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.EventJson;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.KeptFieldSchemas;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.KeptOnlyProjection;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ProbeBody;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ProbeDraftQuery;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ProbeDraftStarted;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ProbeDraftUpdated;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ProbeRules;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ProbeState;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.RaceGate;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.RacingUpdateProbe;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.ReadProbe;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.SeparationObservations;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.StartProbe;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.SubmitProbe;
import uk.gov.justice.laa.dstew.spike.ContentSeparationProbe.UpdateProbe;

/** Gate 4a step 2: schema-kept fields on the event, everything else in a content table. */
@Testcontainers
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ContentSeparationProbe.Config.class, TestJwtDecoderConfig.class})
@Sql(
    statements = {
      "CREATE SCHEMA IF NOT EXISTS spike",
      "CREATE TABLE IF NOT EXISTS spike.probe_event_content ("
          + "event_id text PRIMARY KEY, tag text NOT NULL, content jsonb NOT NULL)",
      "CREATE INDEX IF NOT EXISTS probe_event_content_tag ON spike.probe_event_content (tag)"
    },
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContentSeparationSchemaProbeIntegrationTest {

  private static final ProbeBody BODY =
      new ProbeBody(
          "Ada Lovelace",
          "SW1A 1AA",
          LocalDate.of(1815, 12, 10),
          new BigDecimal("1234.50"),
          null,
          null);
  private static final ProbeBody EDITED =
      new ProbeBody(
          "Ada King",
          "SW1A 2AA",
          LocalDate.of(1815, 12, 10),
          new BigDecimal("2000.00"),
          "Augusta",
          null);
  private static final Instant STARTED = Instant.parse("2026-10-30T17:45:12.123456789Z");

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private CommandGateway commandGateway;
  @Autowired private QueryGateway queryGateway;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SeparationObservations observations;
  @Autowired private KeptFieldSchemas schemas;
  @Autowired private RaceGate gate;
  @Autowired private EventJson eventJson;
  @Autowired private ApplicationDraftStore draftStore;
  @Autowired private KeptOnlyProjection keptOnly;
  @Autowired private ContentReaderProjection contentReader;

  @AfterEach
  void reset() {
    observations.reset();
  }

  @Test
  void givenDraftStarted_whenAppended_thenStoredEventHoldsOnlyKeptFields() {
    UUID draftId = UUID.randomUUID();

    commandGateway.sendAndWait(new StartProbe(draftId, "1A234B", STARTED, BODY));

    observations.print("kept-only");
    assertThat(observations.appendedTypeNames).containsExactly("probe.ProbeDraftStarted");
    List<JsonNode> stored = storedPayloads(draftId);
    assertThat(stored).hasSize(1);
    assertThat(stored.getFirst().propertyNames())
        .containsExactlyInAnyOrder("draftId", "officeCode", "startedAt");
    JsonNode content = contentRows(draftId).getFirst();
    assertThat(content.propertyNames()).containsExactly("body");
    assertThat(content.get("body").get("clientName").asString()).isEqualTo("Ada Lovelace");
  }

  @Test
  void givenAwkwardTypes_whenSplitAndRejoined_thenMatchesAxonFormExactly() {
    UUID draftId = UUID.randomUUID();
    ProbeDraftStarted original =
        new ProbeDraftStarted(draftId, "1A234B", STARTED, Erasable.of(BODY));

    commandGateway.sendAndWait(new StartProbe(draftId, "1A234B", STARTED, BODY));

    EventConverter eventConverter = observations.axonConverter;
    JsonNode axonForm = eventConverter.convert(original, JsonNode.class);
    JsonNode springForm = objectMapper.valueToTree(original);
    ObjectNode rejoined = ((ObjectNode) storedPayloads(draftId).getFirst()).deepCopy();
    rejoined.setAll((ObjectNode) contentRows(draftId).getFirst());
    System.out.printf(
        "PROBE2 converter axon=%s spring=%s same=%s rejoined=%s%n",
        axonForm, springForm, axonForm.equals(springForm), rejoined);
    assertThat(rejoined).isEqualTo(axonForm);
    byte[] direct = eventConverter.convert(original, byte[].class);
    ProbeDraftStarted directBack = eventConverter.convert(direct, ProbeDraftStarted.class);
    ProbeDraftStarted treeBack =
        eventConverter.convert(
            eventConverter.convert(axonForm, byte[].class), ProbeDraftStarted.class);
    System.out.printf(
        "PROBE2 baseline bytes=%s directIncome=%s viaTreeIncome=%s%n",
        new String(direct, java.nio.charset.StandardCharsets.UTF_8),
        directBack.body().value().income(),
        treeBack.body().value().income());
    // Axon is serialising with the mapper we own.
    assertThat(direct).isEqualTo(eventJson.mapper().writeValueAsBytes(original));
    assertThat(treeBack).isEqualTo(original);
    ProbeState state = read(draftId);
    assertThat(state.body().value()).isEqualTo(BODY);
    assertThat(state.body().value().income().scale()).isEqualTo(2);
  }

  @Test
  void givenStartedAndUpdated_whenLoaded_thenContentRejoinedWithOneQuery() {
    UUID draftId = UUID.randomUUID();
    commandGateway.sendAndWait(new StartProbe(draftId, "1A234B", STARTED, BODY));
    commandGateway.sendAndWait(new UpdateProbe(draftId, EDITED));
    observations.reset();

    ProbeState state = read(draftId);

    observations.print("rejoin");
    assertThat(state.revision()).isEqualTo(1);
    assertThat(state.body().value()).isEqualTo(EDITED);
    assertThat(observations.contentQueries.get()).isEqualTo(1);
  }

  @Test
  void givenContentErased_whenLoadedAndSubmitted_thenEntityIsErasedAndSubmitSucceeds() {
    UUID draftId = UUID.randomUUID();
    commandGateway.sendAndWait(new StartProbe(draftId, "1A234B", STARTED, BODY));
    commandGateway.sendAndWait(new UpdateProbe(draftId, EDITED));

    int erased = jdbc.update("DELETE FROM spike.probe_event_content WHERE tag = ?", tag(draftId));
    final ProbeState state = read(draftId);
    commandGateway.sendAndWait(new SubmitProbe(draftId));

    observations.print("erased");
    assertThat(erased).isEqualTo(2);
    assertThat(state.body().isErased()).isTrue();
    assertThat(state.officeCode()).isEqualTo("1A234B");
    assertThat(state.revision()).isEqualTo(1);
    assertThat(read(draftId).submitted()).isTrue();
  }

  @Test
  void givenRacingUpdatesAtOneRevision_whenAppended_thenLoserLeavesNoContent() throws Exception {
    UUID draftId = UUID.randomUUID();
    commandGateway.sendAndWait(new StartProbe(draftId, "1A234B", STARTED, BODY));
    gate.barrier = new CyclicBarrier(2);
    List<Future<String>> results;
    try (var executor = Executors.newFixedThreadPool(2)) {
      results =
          executor.invokeAll(
              List.of(() -> race(draftId, BODY), () -> race(draftId, EDITED)),
              30,
              TimeUnit.SECONDS);
    } finally {
      gate.barrier = null;
    }
    List<String> outcomes = new java.util.ArrayList<>();
    for (Future<String> result : results) {
      outcomes.add(result.get(1, TimeUnit.SECONDS));
    }

    observations.print("race");
    System.out.printf("PROBE2 race outcomes=%s%n", outcomes);
    assertThat(outcomes).containsOnlyOnce("won");
    assertThat(storedPayloads(draftId)).hasSize(2);
    assertThat(contentRows(draftId)).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spike.probe_event_content c"
                    + " LEFT JOIN axon.domain_event_entry e ON e.event_identifier = c.event_id"
                    + " WHERE e.event_identifier IS NULL",
                Integer.class))
        .isZero();
    ProbeBody winner = read(draftId).body().value();
    assertThat(contentRows(draftId))
        .anySatisfy(
            row ->
                assertThat(row.get("body").get("clientName").asString())
                    .isEqualTo(winner.clientName()));
  }

  private String race(UUID draftId, ProbeBody body) {
    try {
      commandGateway.sendAndWait(new RacingUpdateProbe(draftId, UUID.randomUUID(), body));
      return "won";
    } catch (RuntimeException e) {
      Throwable root = e;
      while (root.getCause() != null) {
        root = root.getCause();
      }
      return root.getClass().getSimpleName();
    }
  }

  @Test
  void givenDraftStarted_whenStreamed_thenOnlyRegisteredReaderSeesContent() {
    UUID draftId = UUID.randomUUID();

    commandGateway.sendAndWait(new StartProbe(draftId, "1A234B", STARTED, BODY));

    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> keptOnly.seen.containsKey(draftId) && contentReader.seen.containsKey(draftId));
    System.out.printf(
        "PROBE2 delivery keptOnly=%s contentReader=%s%n",
        keptOnly.seen.get(draftId), contentReader.seen.get(draftId));
    assertThat(keptOnly.seen.get(draftId).isErased()).isTrue();
    assertThat(contentReader.seen.get(draftId).value()).isEqualTo(BODY);
  }

  @Test
  void givenUnregisteredProcessor_whenAskingForContentSource_thenRefused() {
    assertThatThrownBy(
            () ->
                ContentReaders.sourceFor(
                    ContentSeparationProbe.KEPT_ONLY_PROCESSOR, null, null, null, null))
        .hasMessageContaining("is not a registered content reader");
  }

  @Test
  void givenEditedDraft_whenQueried_thenQueryHandlerLoadsWholeEntity() throws Exception {
    UUID draftId = UUID.randomUUID();
    commandGateway.sendAndWait(new StartProbe(draftId, "1A234B", STARTED, BODY));
    for (int i = 0; i < 10; i++) {
      commandGateway.sendAndWait(new UpdateProbe(draftId, EDITED));
    }
    observations.reset();

    ProbeState queried = query(draftId);

    assertThat(queried).isEqualTo(read(draftId));
    assertThat(queried.body().value()).isEqualTo(EDITED);
    assertThat(observations.appendedTypeNames).isEmpty();
    String queryTiming = timing(() -> query(draftId));
    String commandTiming = timing(() -> read(draftId));
    jdbc.update("DELETE FROM spike.probe_event_content WHERE tag = ?", tag(draftId));
    ProbeState erased = query(draftId);
    System.out.printf(
        "PROBE2 query query=%s command=%s erasedBody=%s%n",
        queryTiming, commandTiming, erased.body());
    assertThat(erased.body().isErased()).isTrue();
    assertThat(erased.revision()).isEqualTo(10);
  }

  private ProbeState query(UUID draftId) {
    return queryGateway.query(new ProbeDraftQuery(draftId), ProbeState.class).join();
  }

  @Test
  void givenProbeEventWithoutSchema_whenAppended_thenRejected() {
    UUID draftId = UUID.randomUUID();

    assertThatThrownBy(() -> commandGateway.sendAndWait(new AddProbeNote(draftId, "free text")))
        .hasStackTraceContaining("No kept-field schema for event type probe.ProbeNoteAdded");

    assertThat(storedPayloads(draftId)).isEmpty();
  }

  @Test
  void givenKeptFieldViolatesSchema_whenAppended_thenRejectedAndNothingStored() {
    UUID draftId = UUID.randomUUID();

    assertThatThrownBy(
            () -> commandGateway.sendAndWait(new StartProbe(draftId, "bad", STARTED, BODY)))
        .hasStackTraceContaining("Kept fields of probe.ProbeDraftStarted violate schema");

    assertThat(storedPayloads(draftId)).isEmpty();
    assertThat(contentRows(draftId)).isEmpty();
  }

  @Test
  void givenRealApplicationEvent_whenAppended_thenReportedNotRejected() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());

    commandGateway.sendAndWait(
        new CreateApplicationDraftCommand(
            applicationId,
            "APPLICATION_SUBMITTED",
            request.getLaaReference(),
            request.getApplicationContent(),
            objectMapper.writeValueAsString(request),
            1,
            "BaseCivilApplication.json",
            Instant.now(),
            request.getPotentialDuplicates()));

    observations.print("report-only");
    assertThat(observations.reportedUnclassified).isNotEmpty();
    assertThat(storedPayloads(applicationId)).isNotEmpty();
  }

  @Test
  void givenDeciders_whenReplayedWithContentErased_thenOnlyContentDependentRuleDiffers() {
    UUID draftId = UUID.randomUUID();
    List<Object> history =
        List.of(
            new ProbeDraftStarted(draftId, "1A234B", STARTED, Erasable.of(BODY)),
            new ProbeDraftUpdated(draftId, 1, Erasable.of(EDITED)));

    assertThat(erasureReplay(history, ProbeRules::decideSubmit).consistent()).isTrue();
    Replay wrong = erasureReplay(history, ProbeRules::decideSubmitRequiringClientName);
    System.out.printf("PROBE2 replay wrong=%s%n", wrong);
    assertThat(wrong.consistent()).isFalse();
  }

  @Test
  void givenEditHistories_whenLoaded_thenReportReadCostAgainstTodaysDraftRead() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    createRealDraft(applicationId, request);
    Map<String, Object> application = request.getApplicationContent();
    int bodyBytes = eventJson.mapper().writeValueAsBytes(application).length;
    String baseline = timing(() -> assertThat(draftStore.find(applicationId)).isPresent());

    StringBuilder report = new StringBuilder();
    report.append(
        String.format("bodyBytes=%d baseline(draftStore.find)=%s%n", bodyBytes, baseline));
    for (int edits : new int[] {0, 10, 40, 100}) {
      UUID draftId = UUID.randomUUID();
      commandGateway.sendAndWait(
          new StartProbe(draftId, "1A234B", STARTED, withApplication(BODY, application, 0)));
      for (int i = 1; i <= edits; i++) {
        commandGateway.sendAndWait(
            new UpdateProbe(draftId, withApplication(EDITED, application, i)));
      }
      Long storedBytes =
          jdbc.queryForObject(
              "SELECT coalesce(sum(octet_length(payload)), 0) FROM axon.domain_event_entry"
                  + " WHERE aggregate_identifier = ?",
              Long.class,
              draftId.toString());
      Long contentBytes =
          jdbc.queryForObject(
              "SELECT coalesce(sum(octet_length(content::text)), 0)"
                  + " FROM spike.probe_event_content WHERE tag = ?",
              Long.class,
              tag(draftId));
      String load = timing(() -> assertThat(read(draftId).revision()).isEqualTo(edits));
      report.append(
          String.format(
              "edits=%d events=%d eventBytes=%d contentBytes=%d load=%s%n",
              edits, edits + 1, storedBytes, contentBytes, load));
    }
    System.out.print("PROBE2 readcost\n" + report);
  }

  private static ProbeBody withApplication(
      ProbeBody body, Map<String, Object> application, int edit) {
    Map<String, Object> edited = new java.util.HashMap<>(application);
    edited.put("probeEdit", edit);
    return new ProbeBody(
        body.clientName(),
        body.postcode(),
        body.dateOfBirth(),
        body.income(),
        body.middleName(),
        edited);
  }

  /** Median and p95 in milliseconds over 30 runs, after 5 warm-up runs. */
  private static String timing(Runnable read) {
    for (int i = 0; i < 5; i++) {
      read.run();
    }
    long[] nanos = new long[30];
    for (int i = 0; i < nanos.length; i++) {
      long start = System.nanoTime();
      read.run();
      nanos[i] = System.nanoTime() - start;
    }
    java.util.Arrays.sort(nanos);
    return String.format(
        "median=%.2fms p95=%.2fms", nanos[15] / 1_000_000.0, nanos[28] / 1_000_000.0);
  }

  private void createRealDraft(UUID applicationId, ApplicationCreateRequest request) {
    commandGateway.sendAndWait(
        new CreateApplicationDraftCommand(
            applicationId,
            "APPLICATION_SUBMITTED",
            request.getLaaReference(),
            request.getApplicationContent(),
            objectMapper.writeValueAsString(request),
            1,
            "BaseCivilApplication.json",
            Instant.now(),
            request.getPotentialDuplicates()));
  }

  record Replay(Object withContent, Object erased) {
    boolean consistent() {
      return withContent.equals(erased);
    }
  }

  /** Every decision must be the same whether or not content has been erased. */
  private Replay erasureReplay(
      List<Object> history, BiFunction<ProbeState, UUID, List<Object>> decide) {
    UUID draftId = ((ProbeDraftStarted) history.getFirst()).draftId();
    List<Object> erasedHistory = history.stream().map(schemas::erase).toList();
    return new Replay(
        outcome(ProbeRules.fold(history), draftId, decide),
        outcome(ProbeRules.fold(erasedHistory), draftId, decide));
  }

  private static Object outcome(
      ProbeState state, UUID draftId, BiFunction<ProbeState, UUID, List<Object>> decide) {
    try {
      return decide.apply(state, draftId);
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName();
    }
  }

  private ProbeState read(UUID draftId) {
    return commandGateway.sendAndWait(new ReadProbe(draftId), ProbeState.class);
  }

  private static String tag(UUID draftId) {
    return ContentSeparationProbe.TAG + "=" + draftId;
  }

  private List<JsonNode> storedPayloads(UUID id) {
    return jdbc.query(
        "SELECT convert_from(payload, 'UTF8') AS payload FROM axon.domain_event_entry"
            + " WHERE aggregate_identifier = ? ORDER BY sequence_number",
        (row, index) -> eventJson.mapper().readTree(row.getString("payload")),
        id.toString());
  }

  private List<JsonNode> contentRows(UUID draftId) {
    return jdbc.query(
        "SELECT content::text AS content FROM spike.probe_event_content WHERE tag = ?",
        (row, index) -> eventJson.mapper().readTree(row.getString("content")),
        tag(draftId));
  }
}
