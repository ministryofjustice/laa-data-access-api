package uk.gov.justice.laa.dstew.access.pact;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionUseCase;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkType;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.RecordAutoGrantOutcomeUseCase;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemUseCase;
import uk.gov.justice.laa.dstew.access.controller.application.AutoGrantOutcomeCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateNoteCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.MakeDecisionCommandMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationNotesResult;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindNotesForApplicationQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadRepository;
import uk.gov.justice.laa.dstew.access.testutils.GeneratedRequestFactory;

/**
 * Puts applications into known lifecycle states by dispatching real commands through the real use
 * cases, then waiting for the projections that serve the replayed request.
 *
 * <p>Every method is idempotent and ordered: it inspects the current read model, performs only the
 * steps that are missing, and waits for each one before returning. A state may therefore call the
 * same method repeatedly, and several states may build on the same earlier steps, without conflict.
 *
 * <p>Commands that identify the acting caseworker are built here with {@link
 * PactIds#DEV_CASEWORKER} rather than through the HTTP mappers, because the mappers read the OID
 * from a JWT and state handlers run outside any HTTP request.
 */
class ApplicationStateSeeder {

  static final Duration PROJECTION_WAIT = Duration.ofSeconds(30);
  static final Duration POLL_INTERVAL = Duration.ofMillis(50);

  private final QueryGateway queryGateway;
  private final WorkListItemReadRepository workListItemReadRepository;
  private final CreateApplicationUseCase createApplicationUseCase;
  private final RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase;
  private final AssignWorkItemUseCase assignWorkItemUseCase;
  private final MakeApplicationDecisionUseCase makeApplicationDecisionUseCase;
  private final CreateNoteUseCase createNoteUseCase;
  private final LinkApplicationUseCase linkApplicationUseCase;
  private final CreateApplicationCommandMapper createApplicationCommandMapper;
  private final AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper;
  private final MakeDecisionCommandMapper makeDecisionCommandMapper;
  private final CreateNoteCommandMapper createNoteCommandMapper;
  private final GeneratedRequestFactory requestFactory = new GeneratedRequestFactory("pact");

  ApplicationStateSeeder(
      QueryGateway queryGateway,
      WorkListItemReadRepository workListItemReadRepository,
      CreateApplicationUseCase createApplicationUseCase,
      RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase,
      AssignWorkItemUseCase assignWorkItemUseCase,
      MakeApplicationDecisionUseCase makeApplicationDecisionUseCase,
      CreateNoteUseCase createNoteUseCase,
      LinkApplicationUseCase linkApplicationUseCase,
      CreateApplicationCommandMapper createApplicationCommandMapper,
      AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper,
      MakeDecisionCommandMapper makeDecisionCommandMapper,
      CreateNoteCommandMapper createNoteCommandMapper) {
    this.queryGateway = queryGateway;
    this.workListItemReadRepository = workListItemReadRepository;
    this.createApplicationUseCase = createApplicationUseCase;
    this.recordAutoGrantOutcomeUseCase = recordAutoGrantOutcomeUseCase;
    this.assignWorkItemUseCase = assignWorkItemUseCase;
    this.makeApplicationDecisionUseCase = makeApplicationDecisionUseCase;
    this.createNoteUseCase = createNoteUseCase;
    this.linkApplicationUseCase = linkApplicationUseCase;
    this.createApplicationCommandMapper = createApplicationCommandMapper;
    this.autoGrantOutcomeCommandMapper = autoGrantOutcomeCommandMapper;
    this.makeDecisionCommandMapper = makeDecisionCommandMapper;
    this.createNoteCommandMapper = createNoteCommandMapper;
  }

  /** Submitted, auto-grant outcome still pending. Application version 0. */
  ApplicationReadModel ensureSubmitted(ApplicationCreateRequest request) {
    ApplicationReadModel existing = find(request.getId());
    if (existing != null) {
      return existing;
    }
    boolean projected =
        createApplicationUseCase.execute(createApplicationCommandMapper.toCommand(request, 1));
    if (!projected) {
      throw new IllegalStateException(
          "Projection for application " + request.getId() + " did not become readable in time");
    }
    return awaitApplication(request.getId(), Objects::nonNull);
  }

  /** Manual assessment recorded: on the work list, application version 1. */
  ApplicationReadModel ensureReadyForManualAssessment(ApplicationCreateRequest request) {
    ApplicationReadModel current = ensureSubmitted(request);
    if (current.getAutoGranted() == AutoGrantedState.MANUAL) {
      return current;
    }
    recordAutoGrantOutcomeUseCase.recordReady(
        (MarkApplicationReadyCommand)
            autoGrantOutcomeCommandMapper.toCommand(
                request.getId(), new ManualOutcomeRequest(AutoGrantOutcome.MANUAL)));
    ApplicationReadModel ready =
        awaitApplication(request.getId(), app -> app.getAutoGranted() == AutoGrantedState.MANUAL);
    // The work-list row carries the assignment version later steps need.
    awaitWorkListItem(request.getId(), Objects::nonNull);
    return ready;
  }

  /** Ready for manual assessment and assigned to the dev-token caseworker. */
  ApplicationReadModel ensureAssignedToDevCaseworker(ApplicationCreateRequest request) {
    ApplicationReadModel current = ensureReadyForManualAssessment(request);
    if (PactIds.DEV_CASEWORKER.equals(current.getCaseworkerId())) {
      return current;
    }
    WorkListItemReadModel item = awaitWorkListItem(request.getId(), Objects::nonNull);
    assignWorkItemUseCase.execute(
        new AssignWorkItemCommand(
            request.getId(),
            PactIds.DEV_CASEWORKER,
            item.getAssignmentVersion(),
            null,
            "Assigned during Pact provider state setup",
            Instant.now()));
    return awaitApplication(
        request.getId(), app -> PactIds.DEV_CASEWORKER.equals(app.getCaseworkerId()));
  }

  /** Decided by the dev-token caseworker after manual assessment. GRANTED carries a certificate. */
  ApplicationReadModel ensureDecided(ApplicationCreateRequest request, DecisionStatus decision) {
    ApplicationReadModel current = ensureAssignedToDevCaseworker(request);
    if (current.getDecisionStatus() != null) {
      return current;
    }
    UUID proceedingId = current.getProceedings().getFirst().getId();
    makeApplicationDecisionUseCase.execute(
        makeDecisionCommandMapper.toCommand(
            request.getId(),
            PactIds.DEV_CASEWORKER,
            requestFactory.decision(proceedingId, decision)));
    return awaitApplication(request.getId(), app -> app.getDecisionStatus() != null);
  }

  /** Granted through the automatic path: no assignment, certificate recorded, data version 1. */
  ApplicationReadModel ensureAutoGranted(ApplicationCreateRequest request) {
    ApplicationReadModel current = ensureSubmitted(request);
    if (current.getAutoGranted() == AutoGrantedState.AUTOGRANTED) {
      return current;
    }
    recordAutoGrantOutcomeUseCase.record(
        autoGrantOutcomeCommandMapper.toCommand(
            request.getId(),
            new AutoGrantedOutcomeRequest(
                AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "PACT-CERT-0001"))));
    return awaitApplication(
        request.getId(),
        app ->
            app.getAutoGranted() == AutoGrantedState.AUTOGRANTED && app.getCertificate() != null);
  }

  /** Submitted with one note. */
  void ensureNote(ApplicationCreateRequest request, String noteText) {
    ensureSubmitted(request);
    if (!notes(request.getId()).notes().isEmpty()) {
      return;
    }
    createNoteUseCase.execute(
        createNoteCommandMapper.toCommand(request.getId(), new CreateNoteRequest(noteText)));
    await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(() -> notes(request.getId()), result -> !result.notes().isEmpty());
  }

  /** Both submitted and in one family group, with {@code lead} as the lead application. */
  void ensureLinked(ApplicationCreateRequest lead, ApplicationCreateRequest member) {
    ensureSubmitted(lead);
    ApplicationReadModel currentMember = ensureSubmitted(member);
    if (currentMember.getLinkedGroupId() != null) {
      return;
    }
    // Linking two standalone applications makes the target the lead of the new group.
    linkApplicationUseCase.execute(
        new LinkApplicationCommand(
            member.getId(), lead.getId(), LinkType.FAMILY, null, Instant.now()));
    awaitApplication(member.getId(), app -> lead.getId().equals(app.getLeadApplicationId()));
    awaitApplication(lead.getId(), app -> app.getLinkedGroupId() != null);
  }

  /** Waits until the list projection returns the application in an unfiltered first page. */
  void awaitListed(UUID applicationId) {
    await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(
            () ->
                queryGateway
                    .query(
                        new FindAllApplicationsQuery(
                            null, null, null, null, null, null, null, null, null, 1, 100),
                        FindAllApplicationsResult.class)
                    .join(),
            result ->
                result != null
                    && result.applications().stream()
                        .anyMatch(app -> applicationId.equals(app.getApplicationId())));
  }

  /** Waits until the individuals query can return the application's client. */
  void awaitClientIndividual(UUID applicationId) {
    await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(
            () ->
                queryGateway
                    .query(
                        new FindIndividualsQuery(applicationId, "CLIENT", false, 1, 10),
                        FindIndividualsResult.class)
                    .join(),
            result -> result != null && result.client() != null);
  }

  WorkListItemReadModel awaitWorkListItem(
      UUID itemId, java.util.function.Predicate<WorkListItemReadModel> condition) {
    return await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(() -> workListItemReadRepository.findById(itemId).orElse(null), condition);
  }

  ApplicationReadModel awaitApplication(
      UUID applicationId, java.util.function.Predicate<ApplicationReadModel> condition) {
    return await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(() -> find(applicationId), app -> app != null && condition.test(app));
  }

  ApplicationReadModel find(UUID applicationId) {
    return queryGateway
        .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
        .join();
  }

  private ApplicationNotesResult notes(UUID applicationId) {
    return queryGateway
        .query(new FindNotesForApplicationQuery(applicationId), ApplicationNotesResult.class)
        .join();
  }
}
