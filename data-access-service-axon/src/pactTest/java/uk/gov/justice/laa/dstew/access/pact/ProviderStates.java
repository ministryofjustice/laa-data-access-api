package uk.gov.justice.laa.dstew.access.pact;

import static uk.gov.justice.laa.dstew.access.pact.PactApplicationFixtures.submittedApplication;
import static uk.gov.justice.laa.dstew.access.pact.PactApplicationFixtures.submittedApplication001;

import java.util.Map;
import java.util.UUID;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;

/**
 * One method per provider state, each building its data through the seeders. {@link
 * DataAccessApiProviderTests} maps the state strings in {@link PactStates} onto these methods, and
 * {@link PactProviderBootCheckTest} runs every one of them without the broker.
 */
class ProviderStates {

  private static final Logger log = LoggerFactory.getLogger(ProviderStates.class);
  private static final String APPLICATION_PROJECTION = "application-projection";

  private final ApplicationStateSeeder applications;
  private final PriorAuthorityStateSeeder priorAuthorities;
  private final AxonConfiguration axonConfiguration;

  ProviderStates(
      ApplicationStateSeeder applications,
      PriorAuthorityStateSeeder priorAuthorities,
      AxonConfiguration axonConfiguration) {
    this.applications = applications;
    this.priorAuthorities = priorAuthorities;
    this.axonConfiguration = axonConfiguration;
  }

  // ─── Applications ────────────────────────────────────────────────────────────

  /** Application 001, awaiting manual assessment, visible in the list. */
  void applicationsExist() {
    log.info("State: {}", PactStates.APPLICATIONS_EXIST);
    applications.ensureReadyForManualAssessment(submittedApplication001());
    applications.awaitListed(PactIds.APPLICATION_001);
  }

  /** Application 001 with client Alice Anderson. */
  void clientIndividualExistsForApplication001() {
    log.info("State: {}", PactStates.CLIENT_INDIVIDUAL_EXISTS_FOR_APPLICATION_001);
    applications.ensureSubmitted(submittedApplication001());
    applications.awaitClientIndividual(PactIds.APPLICATION_001);
  }

  /** Nothing to set up: the consumer supplies an ID the database has never seen. */
  void noMatchingSpecialChildrenActApplicationExists() {
    log.info("State: {}", PactStates.NO_MATCHING_SCA_APPLICATION_EXISTS);
  }

  /** Application 002: submitted, auto-grant pending, version 0, no notes, no decision. */
  void submittedApplicationExists() {
    log.info("State: {}", PactStates.APPLICATION_SUBMITTED_EXISTS);
    applications.ensureSubmitted(application(PactIds.APPLICATION_SUBMITTED, "0002", "Bo", "Burns"));
  }

  /** Application 003: manual assessment, assigned to the dev caseworker, version 1. */
  void applicationAssignedExists() {
    log.info("State: {}", PactStates.APPLICATION_ASSIGNED_EXISTS);
    applications.ensureAssignedToDevCaseworker(
        application(PactIds.APPLICATION_ASSIGNED, "0003", "Cara", "Cole"));
  }

  /** Application 004: manual assessment, unassigned, assignment version 0. */
  void applicationUnassignedExists() {
    log.info("State: {}", PactStates.APPLICATION_UNASSIGNED_EXISTS);
    applications.ensureReadyForManualAssessment(
        application(PactIds.APPLICATION_UNASSIGNED, "0004", "Dev", "Dunn"));
  }

  /** Application 005: granted after manual assessment, with a certificate. */
  void applicationGrantedExists() {
    log.info("State: {}", PactStates.APPLICATION_GRANTED_EXISTS);
    applications.ensureDecided(
        application(PactIds.APPLICATION_GRANTED, "0005", "Eve", "Ellis"), DecisionStatus.GRANTED);
  }

  /** Application 006: submitted with one note. */
  void applicationWithNotesExists() {
    log.info("State: {}", PactStates.APPLICATION_WITH_NOTES_EXISTS);
    applications.ensureNote(
        application(PactIds.APPLICATION_WITH_NOTES, "0006", "Finn", "Ford"),
        "Note added during Pact provider state setup");
  }

  /** Applications 007 (lead) and 008 (member) in one family group. */
  void applicationsLinked() {
    log.info("State: {}", PactStates.APPLICATIONS_LINKED);
    applications.ensureLinked(
        application(PactIds.APPLICATION_LEAD, "0007", "Gia", "Grey"),
        application(PactIds.APPLICATION_MEMBER, "0008", "Hal", "Hart"));
  }

  /** Nothing to set up: the ID is reserved and never created. */
  void noApplicationWithId() {
    log.info("State: {}", PactStates.NO_APPLICATION_WITH_ID);
  }

  /**
   * Pauses the application projection so a create cannot observe its read model within the
   * configured wait and returns 202 instead of 201. {@link #applicationReadModelCaughtUp()} must
   * run afterwards.
   */
  void applicationReadModelLagging() {
    log.info("State: {}", PactStates.APPLICATION_READ_MODEL_LAGGING);
    applicationProjection().shutdown().join();
  }

  /** Teardown for {@link #applicationReadModelLagging()}: restarts the projection. */
  void applicationReadModelCaughtUp() {
    StreamingEventProcessor processor = applicationProjection();
    if (!processor.isRunning()) {
      processor.start().join();
    }
  }

  // ─── Work list ───────────────────────────────────────────────────────────────

  /** One assigned application, one unassigned application and one submitted prior authority. */
  void workListItemsExist() {
    log.info("State: {}", PactStates.WORK_LIST_ITEMS_EXIST);
    applicationAssignedExists();
    applicationUnassignedExists();
    priorAuthoritySubmittedExists();
  }

  // ─── Prior authorities ───────────────────────────────────────────────────────

  /** Prior authority a001: an EXPERT draft on the granted parent application 009. */
  void priorAuthorityDraftExists() {
    log.info("State: {}", PactStates.PRIOR_AUTHORITY_DRAFT_EXISTS);
    priorAuthorities.ensureDraft(PactIds.PRIOR_AUTHORITY_DRAFT, priorAuthorityParent());
  }

  /** Prior authority a002: submitted, unassigned, version 0. */
  void priorAuthoritySubmittedExists() {
    log.info("State: {}", PactStates.PRIOR_AUTHORITY_SUBMITTED_EXISTS);
    priorAuthorities.ensureSubmitted(PactIds.PRIOR_AUTHORITY_SUBMITTED, priorAuthorityParent());
  }

  /** Prior authority a003: submitted and assigned to the dev caseworker, version 0. */
  void priorAuthorityAssignedExists() {
    log.info("State: {}", PactStates.PRIOR_AUTHORITY_ASSIGNED_EXISTS);
    priorAuthorities.ensureAssignedToDevCaseworker(
        PactIds.PRIOR_AUTHORITY_ASSIGNED, priorAuthorityParent());
  }

  /** Prior authority a004: granted. */
  void priorAuthorityDecidedExists() {
    log.info("State: {}", PactStates.PRIOR_AUTHORITY_DECIDED_EXISTS);
    priorAuthorities.ensureGranted(PactIds.PRIOR_AUTHORITY_DECIDED, priorAuthorityParent());
  }

  /** Prior authority a005: a draft with one uploaded PDF. Returns the generated document ID. */
  Map<String, Object> priorAuthorityWithDocumentExists() {
    log.info("State: {}", PactStates.PRIOR_AUTHORITY_WITH_DOCUMENT_EXISTS);
    UUID documentId =
        priorAuthorities.ensureUploadedDocument(
            PactIds.PRIOR_AUTHORITY_WITH_DOCUMENT, priorAuthorityParent());
    return Map.of("documentId", documentId.toString());
  }

  /** Nothing to set up: the ID is reserved and never created. */
  void noPriorAuthorityWithId() {
    log.info("State: {}", PactStates.NO_PRIOR_AUTHORITY_WITH_ID);
  }

  /**
   * Application 009, the granted parent of every seeded prior authority, with at least one draft
   * and one submitted prior authority attached, so GET application by id returns a populated list.
   */
  void applicationWithPriorAuthorities() {
    log.info("State: {}", PactStates.APPLICATION_WITH_PRIOR_AUTHORITIES);
    priorAuthorityDraftExists();
    priorAuthoritySubmittedExists();
    applications.awaitApplication(
        PactIds.APPLICATION_PA_PARENT, app -> app.getDecisionStatus() != null);
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  private static ApplicationCreateRequest priorAuthorityParent() {
    return application(PactIds.APPLICATION_PA_PARENT, "0009", "Ida", "Ince");
  }

  private static ApplicationCreateRequest application(
      UUID id, String referenceSuffix, String firstName, String lastName) {
    return submittedApplication(id, "LAA-REF-" + referenceSuffix, firstName, lastName);
  }

  private StreamingEventProcessor applicationProjection() {
    StreamingEventProcessor processor =
        axonConfiguration.getComponents(StreamingEventProcessor.class).get(APPLICATION_PROJECTION);
    if (processor == null) {
      throw new IllegalStateException("No event processor named " + APPLICATION_PROJECTION);
    }
    return processor;
  }
}
