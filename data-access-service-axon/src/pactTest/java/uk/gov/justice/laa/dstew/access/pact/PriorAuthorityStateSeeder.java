package uk.gov.justice.laa.dstew.access.pact;

import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.pact.ApplicationStateSeeder.POLL_INTERVAL;
import static uk.gov.justice.laa.dstew.access.pact.ApplicationStateSeeder.PROJECTION_WAIT;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemUseCase;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.controller.application.SavePriorAuthorityDraftCommandMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.ExpertCosts;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityByPriorAuthorityIdQuery;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;

/**
 * Puts prior authorities into known lifecycle states through the real use cases.
 *
 * <p>The API generates prior authority identifiers, but seeding may fix them so consumers get
 * stable identifiers for reads. Every prior authority here belongs to one shared, auto-granted
 * parent application, because a draft can only be started against a granted application.
 *
 * <p>Methods are idempotent and build on each other in lifecycle order: draft, submitted, assigned,
 * decided. Uploaded document identifiers remain server-generated and are returned so the state
 * handler can hand them to Pact as provider state injected values.
 */
class PriorAuthorityStateSeeder {

  static final String DRAFT_STATUS = "DRAFT";
  static final String SUBMITTED_STATUS = "SUBMITTED";
  static final String DECIDED_STATUS = "DECIDED";

  private final QueryGateway queryGateway;
  private final ApplicationStateSeeder applications;
  private final CreatePriorAuthorityDraftUseCase createDraftUseCase;
  private final SubmitPriorAuthorityDraftUseCase submitDraftUseCase;
  private final MakePriorAuthorityDecisionUseCase makeDecisionUseCase;
  private final UploadPriorAuthorityDocumentUseCase uploadDocumentUseCase;
  private final AssignWorkItemUseCase assignWorkItemUseCase;
  private final SavePriorAuthorityDraftCommandMapper draftCommandMapper;

  PriorAuthorityStateSeeder(
      QueryGateway queryGateway,
      ApplicationStateSeeder applications,
      CreatePriorAuthorityDraftUseCase createDraftUseCase,
      SubmitPriorAuthorityDraftUseCase submitDraftUseCase,
      MakePriorAuthorityDecisionUseCase makeDecisionUseCase,
      UploadPriorAuthorityDocumentUseCase uploadDocumentUseCase,
      AssignWorkItemUseCase assignWorkItemUseCase,
      SavePriorAuthorityDraftCommandMapper draftCommandMapper) {
    this.queryGateway = queryGateway;
    this.applications = applications;
    this.createDraftUseCase = createDraftUseCase;
    this.submitDraftUseCase = submitDraftUseCase;
    this.makeDecisionUseCase = makeDecisionUseCase;
    this.uploadDocumentUseCase = uploadDocumentUseCase;
    this.assignWorkItemUseCase = assignWorkItemUseCase;
    this.draftCommandMapper = draftCommandMapper;
  }

  /** An EXPERT draft, complete enough to submit, on a granted parent application. */
  PriorAuthorityResult ensureDraft(UUID priorAuthorityId, ApplicationCreateRequest parent) {
    PriorAuthorityResult existing = find(priorAuthorityId);
    if (existing != null) {
      return existing;
    }
    applications.ensureAutoGranted(parent);
    CreatePriorAuthorityDraftCommand generated =
        draftCommandMapper.toCreateCommand(expertDraftRequest(parent.getId()));
    createDraftUseCase.execute(
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            generated.applicationId(),
            generated.content(),
            generated.serialisedRequest(),
            generated.schemaVersion(),
            generated.schemaName(),
            generated.occurredAt()));
    return awaitPriorAuthority(priorAuthorityId, Objects::nonNull);
  }

  /** Submitted: content sealed as data version 0, on the work list, unassigned. */
  PriorAuthorityResult ensureSubmitted(UUID priorAuthorityId, ApplicationCreateRequest parent) {
    PriorAuthorityResult current = ensureDraft(priorAuthorityId, parent);
    if (!DRAFT_STATUS.equals(current.status())) {
      return current;
    }
    boolean projected =
        submitDraftUseCase.submit(
            new SubmitPriorAuthorityDraftCommand(priorAuthorityId, Instant.now()));
    if (!projected) {
      throw new IllegalStateException(
          "Projection for prior authority "
              + priorAuthorityId
              + " did not become readable in time");
    }
    PriorAuthorityResult submitted =
        awaitPriorAuthority(priorAuthorityId, pa -> !DRAFT_STATUS.equals(pa.status()));
    applications.awaitWorkListItem(priorAuthorityId, Objects::nonNull);
    return submitted;
  }

  /** Submitted and assigned to the dev-token caseworker. */
  PriorAuthorityResult ensureAssignedToDevCaseworker(
      UUID priorAuthorityId, ApplicationCreateRequest parent) {
    PriorAuthorityResult current = ensureSubmitted(priorAuthorityId, parent);
    if (!SUBMITTED_STATUS.equals(current.status())) {
      return current;
    }
    WorkListItemReadModel item = applications.awaitWorkListItem(priorAuthorityId, Objects::nonNull);
    if (PactIds.DEV_CASEWORKER.equals(item.getAssigneeId())) {
      return current;
    }
    assignWorkItemUseCase.execute(
        new AssignWorkItemCommand(
            priorAuthorityId,
            PactIds.DEV_CASEWORKER,
            item.getAssignmentVersion(),
            null,
            "Assigned during Pact provider state setup",
            Instant.now()));
    applications.awaitWorkListItem(
        priorAuthorityId, row -> PactIds.DEV_CASEWORKER.equals(row.getAssigneeId()));
    return current;
  }

  /** Granted by the dev-token caseworker. */
  PriorAuthorityResult ensureGranted(UUID priorAuthorityId, ApplicationCreateRequest parent) {
    PriorAuthorityResult current = ensureAssignedToDevCaseworker(priorAuthorityId, parent);
    if (DECIDED_STATUS.equals(current.status())) {
      return current;
    }
    Instant now = Instant.now();
    makeDecisionUseCase.execute(
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            PactIds.DEV_CASEWORKER,
            0L,
            "GRANTED",
            "Granted during Pact provider state setup",
            BigDecimal.valueOf(1500),
            null,
            null,
            null,
            now,
            "{}",
            now));
    return awaitPriorAuthority(priorAuthorityId, pa -> DECIDED_STATUS.equals(pa.status()));
  }

  /**
   * A draft with one uploaded PDF. The document identifier is generated by the service, so it is
   * returned for Pact to inject into the consumer's request.
   */
  UUID ensureUploadedDocument(UUID priorAuthorityId, ApplicationCreateRequest parent) {
    PriorAuthorityResult current = ensureDraft(priorAuthorityId, parent);
    if (current.uploadedDocuments() != null && !current.uploadedDocuments().isEmpty()) {
      return current.uploadedDocuments().getFirst().documentId();
    }
    MockMultipartFile evidence =
        new MockMultipartFile(
            "file",
            "evidence.pdf",
            "application/pdf",
            "%PDF-1.4\nPact provider state evidence".getBytes(StandardCharsets.US_ASCII));
    UUID documentId =
        uploadDocumentUseCase.execute(priorAuthorityId, evidence, "CIVIL_APPLY").documentId();
    awaitPriorAuthority(
        priorAuthorityId,
        pa ->
            pa.uploadedDocuments() != null
                && pa.uploadedDocuments().stream()
                    .anyMatch(document -> documentId.equals(document.documentId())));
    return documentId;
  }

  PriorAuthorityResult find(UUID priorAuthorityId) {
    return queryGateway
        .query(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
            PriorAuthorityResult.class)
        .join();
  }

  private PriorAuthorityResult awaitPriorAuthority(
      UUID priorAuthorityId, Predicate<PriorAuthorityResult> condition) {
    return await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(() -> find(priorAuthorityId), pa -> pa != null && condition.test(pa));
  }

  private static CreatePriorAuthorityDraftRequest expertDraftRequest(UUID applicationId) {
    return CreatePriorAuthorityDraftRequest.builder()
        .applicationId(applicationId)
        .priorAuthorityType(PriorAuthorityType.EXPERT)
        .justification("Expert witness required for the final hearing")
        .expertDetails(
            ExpertDetails.builder()
                .expertType("Forensic Accountant")
                .expertFullName("Jane Smith")
                .expertPostcode("SW1A 1AA")
                .expertCosts(
                    ExpertCosts.builder()
                        .billingType(BillingType.FIXED_RATE)
                        .totalAmount(BigDecimal.valueOf(1500))
                        .costsSharedWithOtherParties(false)
                        .build())
                .build())
        .build();
  }
}
