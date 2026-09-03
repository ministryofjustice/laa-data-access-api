package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Unit tests for {@link ApplicationDecider}. */
class ApplicationDeciderTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-07-28T10:00:00Z");

  // ── decideCreate ───────────────────────────────────────────────────────────────

  @Test
  void givenEmptyState_whenDecideCreate_thenReturnsApplicationCreatedEvent() {
    ApplicationState state = new ApplicationState();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreationDetails details = minimalDetails(applicationId, null);
    String fingerprint = ApplicationDataStore.fingerprint("{}");

    List<Object> events =
        ApplicationDecider.decideCreate(state, applicationId, 1, fingerprint, details, 0L);

    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).isInstanceOf(ApplicationCreatedEvent.class);
    ApplicationCreatedEvent event = (ApplicationCreatedEvent) events.getFirst();
    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.requestFingerprint()).isEqualTo(fingerprint);
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.applicationDataVersion()).isEqualTo(0L);
  }

  @Test
  void givenExistingStateWithMatchingFingerprint_whenDecideCreate_thenReturnsEmptyList() {
    UUID applicationId = UUID.randomUUID();
    String fingerprint = ApplicationDataStore.fingerprint("{}");
    ApplicationState state = stateAfterCreate(applicationId, fingerprint, 1);

    List<Object> events =
        ApplicationDecider.decideCreate(state, applicationId, 1, fingerprint, null, 0L);

    assertThat(events).isEmpty();
  }

  @Test
  void givenExistingStateWithDifferentFingerprint_whenDecideCreate_thenThrowsConflict() {
    UUID applicationId = UUID.randomUUID();
    String originalFingerprint = ApplicationDataStore.fingerprint("{}");
    ApplicationState state = stateAfterCreate(applicationId, originalFingerprint, 1);
    String differentFingerprint = ApplicationDataStore.fingerprint("{\"different\":true}");

    assertThatThrownBy(
            () ->
                ApplicationDecider.decideCreate(
                    state, applicationId, 1, differentFingerprint, null, 0L))
        .isInstanceOf(ApplicationCreationConflictException.class);
  }

  @Test
  void givenExistingStateWithDifferentSchemaVersion_whenDecideCreate_thenThrowsConflict() {
    UUID applicationId = UUID.randomUUID();
    String fingerprint = ApplicationDataStore.fingerprint("{}");
    ApplicationState state = stateAfterCreate(applicationId, fingerprint, 1);

    assertThatThrownBy(
            () -> ApplicationDecider.decideCreate(state, applicationId, 2, fingerprint, null, 0L))
        .isInstanceOf(ApplicationCreationConflictException.class);
  }

  @Test
  void givenSelfReferentialLead_whenDecideCreate_thenThrowsGroupInvariant() {
    ApplicationState state = new ApplicationState();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreationDetails details = minimalDetails(applicationId, applicationId); // self-lead
    String fingerprint = ApplicationDataStore.fingerprint("{}");

    assertThatThrownBy(
            () ->
                ApplicationDecider.decideCreate(state, applicationId, 1, fingerprint, details, 0L))
        .isInstanceOf(ApplicationGroupInvariantException.class);
  }

  // ── decideCreateLinkedGroup ────────────────────────────────────────────────────

  @Test
  void givenLeadApplication_whenDecideCreateLinkedGroup_thenReturnsRequested() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    UUID groupId = UUID.randomUUID();
    List<UUID> members = List.of(applicationId, UUID.randomUUID());
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    LinkedApplicationGroupRequested event =
        ApplicationDecider.decideCreateLinkedGroup(state, groupId, members, occurredAt);

    assertThat(event.groupId()).isEqualTo(groupId);
    assertThat(event.leadApplicationId()).isEqualTo(applicationId);
    assertThat(event.memberApplicationIds()).isEqualTo(members);
    assertThat(event.occurredAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenAssociatedMember_whenDecideCreateLinkedGroup_thenThrowsGroupInvariant() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    state.isAssociatedMember = true;

    assertThatThrownBy(
            () ->
                ApplicationDecider.decideCreateLinkedGroup(
                    state, UUID.randomUUID(), List.of(applicationId), TIMESTAMP))
        .isInstanceOf(ApplicationGroupInvariantException.class);
  }

  // ── decideDecision ─────────────────────────────────────────────────────────────

  @Test
  void givenCorrectVersion_whenDecideDecision_thenReturnsDecisionMadeEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    state.applicationVersion = 0L;
    state.applicationDataVersion = 0L;
    ApplicationDataPayload current = payloadWithProceeding(applicationId, proceedingId);
    Instant occurredAt = Instant.parse("2026-07-19T10:00:00Z");

    ApplicationDecisionMadeEvent event =
        ApplicationDecider.decideDecision(
            state,
            new MakeApplicationDecisionCommand(
                applicationId,
                0L,
                "REFUSED",
                false,
                List.of(new MakeDecisionProceeding(proceedingId, "REFUSED", "reason", "just")),
                null,
                "{}",
                "desc",
                occurredAt),
            current);

    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.applicationVersion()).isEqualTo(1L);
    assertThat(event.applicationDataVersion()).isEqualTo(1L);
    assertThat(event.overallDecision()).isEqualTo("REFUSED");
    assertThat(event.occurredAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenStaleVersion_whenDecideDecision_thenThrowsVersionConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    state.applicationVersion = 0L;
    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId,
            1L, // stale: actual is 0
            "REFUSED",
            false,
            List.of(new MakeDecisionProceeding(UUID.randomUUID(), "REFUSED", "r", "just")),
            null,
            "{}",
            null,
            TIMESTAMP);
    ApplicationDataPayload current = payloadWithProceeding(applicationId, UUID.randomUUID());

    assertThatThrownBy(() -> ApplicationDecider.decideDecision(state, command, current))
        .isInstanceOf(ApplicationVersionConflictException.class);
  }

  @Test
  void givenEmptyProceedings_whenDecideDecision_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId, 0L, "REFUSED", false, List.of(), null, "{}", null, TIMESTAMP);
    ApplicationDataPayload current = payloadWithProceeding(applicationId, UUID.randomUUID());

    assertThatThrownBy(() -> ApplicationDecider.decideDecision(state, command, current))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void givenGrantedWithoutCertificate_whenDecideDecision_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId,
            0L,
            "GRANTED",
            false,
            List.of(new MakeDecisionProceeding(proceedingId, "GRANTED", "r", "just")),
            null, // missing certificate
            "{}",
            null,
            TIMESTAMP);
    ApplicationDataPayload current = payloadWithProceeding(applicationId, proceedingId);

    assertThatThrownBy(() -> ApplicationDecider.decideDecision(state, command, current))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void givenUnknownProceedingId_whenDecideDecision_thenThrowsResourceNotFound() {
    UUID applicationId = UUID.randomUUID();
    UUID knownProceedingId = UUID.randomUUID();
    UUID unknownProceedingId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId,
            0L,
            "REFUSED",
            false,
            List.of(new MakeDecisionProceeding(unknownProceedingId, "REFUSED", "r", "just")),
            null,
            "{}",
            null,
            TIMESTAMP);
    ApplicationDataPayload current = payloadWithProceeding(applicationId, knownProceedingId);

    assertThatThrownBy(() -> ApplicationDecider.decideDecision(state, command, current))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void givenDuplicateProceedingIds_whenDecideDecision_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    MakeDecisionProceeding proceeding =
        new MakeDecisionProceeding(proceedingId, "REFUSED", "reason", "just");
    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId,
            0L,
            "REFUSED",
            false,
            List.of(proceeding, proceeding),
            null,
            "{}",
            null,
            TIMESTAMP);
    ApplicationDataPayload current = payloadWithProceeding(applicationId, proceedingId);

    assertThatThrownBy(() -> ApplicationDecider.decideDecision(state, command, current))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void
      givenActiveManualApplicationAssignedToCaller_whenValidatingManualDecision_thenAllowsDecision() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    state.autoGranted = AutoGrantedState.MANUAL;
    state.caseworkerId = caseworkerId;

    ApplicationDecider.validateManualDecisionAssignment(
        state,
        new MakeApplicationDecisionCommand(
            applicationId, caseworkerId, 0L, "REFUSED", List.of(), null, "{}", null, TIMESTAMP));
  }

  @Test
  void
      givenActiveManualApplicationAssignedToAnotherCaseworker_whenValidatingManualDecision_thenConflicts() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    state.autoGranted = AutoGrantedState.MANUAL;
    state.caseworkerId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                ApplicationDecider.validateManualDecisionAssignment(
                    state,
                    new MakeApplicationDecisionCommand(
                        applicationId,
                        UUID.randomUUID(),
                        0L,
                        "REFUSED",
                        List.of(),
                        null,
                        "{}",
                        null,
                        TIMESTAMP)))
        .isInstanceOf(
            uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException
                .class);
  }

  // ── decideNote ─────────────────────────────────────────────────────────────────

  @Test
  void givenApplication_whenDecideNote_thenReturnsNoteCreatedEvent() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    state.applicationDataVersion = 0L;

    NoteCreatedEvent event =
        ApplicationDecider.decideNote(
            state, new CreateNoteCommand(applicationId, "My note", "{}", TIMESTAMP));

    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.applicationDataVersion()).isEqualTo(1L);
    assertThat(event.occurredAt()).isEqualTo(TIMESTAMP);
  }

  // ── validateGranted ────────────────────────────────────────────────────────────

  @Test
  void givenGrantedDecision_whenValidateGranted_thenReturnsNormally() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    state.overallDecision = "GRANTED";

    ApplicationDecider.validateGranted(state);
    // no exception — test passes
  }

  @Test
  void givenNonGrantedDecision_whenValidateGranted_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    state.overallDecision = "REFUSED";

    assertThatThrownBy(() -> ApplicationDecider.validateGranted(state))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            ex ->
                assertThat(((ValidationException) ex).errors())
                    .containsExactly(
                        "Prior authority requires the application to have an overall decision of GRANTED"));
  }

  @Test
  void givenNoDecision_whenValidateGranted_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = stateAfterCreate(applicationId, "fp", 1);
    // state.overallDecision is null by default

    assertThatThrownBy(() -> ApplicationDecider.validateGranted(state))
        .isInstanceOf(ValidationException.class);
  }

  // ── helpers ────────────────────────────────────────────────────────────────────

  private static ApplicationState stateAfterCreate(
      UUID applicationId, String fingerprint, int schemaVersion) {
    ApplicationState state = new ApplicationState();
    state.applicationId = applicationId;
    state.requestFingerprint = fingerprint;
    state.schemaVersion = schemaVersion;
    state.applicationVersion = 0L;
    state.applicationDataVersion = 0L;
    return state;
  }

  private static ApplicationCreationDetails minimalDetails(
      UUID applicationId, UUID leadApplicationId) {
    return new ApplicationCreationDetails(
        "APPLICATION_SUBMITTED",
        "LAA-123",
        null,
        uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider.builder()
            .officeCode("1A001B")
            .build(),
        null,
        null,
        1,
        Instant.parse("2026-07-14T12:30:00Z"),
        false,
        null,
        null,
        List.of(),
        "{}",
        Instant.parse("2026-07-15T08:00:00Z"),
        leadApplicationId);
  }

  private static ApplicationDataPayload payloadWithProceeding(
      UUID applicationId, UUID proceedingId) {
    ApplicationCreationDetails details =
        new ApplicationCreationDetails(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            null,
            uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider.builder()
                .officeCode("1A001B")
                .build(),
            null,
            null,
            1,
            Instant.parse("2026-07-14T12:30:00Z"),
            false,
            null,
            null,
            List.of(
                uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding.builder()
                    .id(proceedingId)
                    .leadProceeding(true)
                    .description("Care order")
                    .code("SE003")
                    .build()),
            "{}",
            Instant.parse("2026-07-15T08:00:00Z"),
            null);
    return ApplicationDataPayload.from(details);
  }
}
