package uk.gov.justice.laa.dstew.access.command.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.AssignCaseworkerToApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.assignment.UnassignCaseworkerFromApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.CreateLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.exception.ApplicationAutoGrantOutcomeConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.InvalidApplicationStateException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class ApplicationAggregateTest {

  private AxonTestFixture fixture;
  private ApplicationDataStore applicationDataStore;

  @BeforeEach
  void setUp() {
    ApplicationCreationDetailsFactory factory =
        new ApplicationCreationDetailsFactory(null, null) {
          @Override
          public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
            return applicationCreationDetails(command.applicationId());
          }
        };
    applicationDataStore = mock(ApplicationDataStore.class);
    when(applicationDataStore.append(any(), anyLong(), any()))
        .thenAnswer(
            invocation ->
                ApplicationDataStore.fingerprint(
                    invocation.<ApplicationCreationDetails>getArgument(2).serialisedRequest()));
    fixture = fixtureWith(factory);
  }

  @Test
  void givenNoApplication_whenCreated_thenCreatesApplication() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    ApplicationCreatedEvent expected = applicationCreatedEvent(applicationId, details);

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(createCommand(applicationId, "{}"))
        .then()
        .resultMessagePayload(applicationId)
        .events(expected);
  }

  @Test
  void givenLeadApplication_whenCreated_thenCreatesApplicationWithLeadId() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationCreationDetails detailsWithLead =
        new ApplicationCreationDetails(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            null,
            ApplicationProvider.builder().officeCode("1A001B").build(),
            null,
            null,
            1,
            java.time.Instant.parse("2026-07-14T12:30:00Z"),
            false,
            null,
            null,
            List.of(),
            "{}",
            java.time.Instant.parse("2026-07-15T08:00:00Z"),
            leadApplicationId);

    ApplicationCreationDetailsFactory factoryWithLead =
        new ApplicationCreationDetailsFactory(null, null) {
          @Override
          public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
            return detailsWithLead;
          }
        };
    fixture.stop();
    fixture = fixtureWith(factoryWithLead);

    ApplicationCreatedEvent createdEvent = applicationCreatedEvent(applicationId, detailsWithLead);

    // ApplicationLinkedEvent is no longer emitted; linking is initiated by
    // ApplicationGroupEventRouter after the projection picks up ApplicationCreatedEvent.
    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(createCommand(applicationId, "{}"))
        .then()
        .resultMessagePayload(applicationId)
        .events(createdEvent);
  }

  @Test
  void givenExistingApplication_whenIdenticalRetry_thenSucceedsIdempotently() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given()
        .events(existing)
        .when()
        .command(createCommand(applicationId, "{}"))
        .then()
        .resultMessagePayload(applicationId)
        .noEvents();
  }

  @Test
  void givenExistingApplication_whenDifferentSerialisedRequest_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given()
        .events(existing)
        .when()
        .command(createCommand(applicationId, "{\"different\":true}"))
        .then()
        .exception(ApplicationCreationConflictException.class)
        .noEvents();
  }

  @Test
  void givenExistingApplication_whenDifferentSchemaVersion_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given()
        .events(existing)
        .when()
        .command(createCommandWithSchema(applicationId, "{}", 2))
        .then()
        .exception(ApplicationCreationConflictException.class)
        .noEvents();
  }

  @Test
  void givenCurrentApplicationVersion_whenDecisionMade_thenStoresNextVersionAndEmitsThinEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:15:00Z");
    Instant readyAt = Instant.parse("2026-07-19T10:00:00Z");
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    ApplicationCreatedEvent created = applicationCreatedEvent(applicationId, details);
    ApplicationDataPayload current =
        ApplicationDataPayload.from(details).withManualAssessmentRequired();
    when(applicationDataStore.get(applicationId, 1L)).thenReturn(current);
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any())).thenReturn("hash");

    fixture
        .given()
        .events(
            created, new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, readyAt))
        .when()
        .command(
            new MakeApplicationDecisionCommand(
                applicationId,
                1L,
                "REFUSED",
                false,
                List.of(
                    new MakeDecisionProceeding(proceedingId, "REFUSED", "reason", "justification")),
                null,
                "{\"overallDecision\":\"REFUSED\"}",
                "Decision recorded",
                occurredAt))
        .then()
        .events(
            new ApplicationDecisionMadeEvent(
                applicationId, 2L, 2L, "REFUSED", AutoGrantedState.MANUAL, occurredAt));
  }

  @Test
  void givenPendingAutoGrantOutcome_whenDecisionMade_thenRejectsWithoutReadingData() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:15:00Z");
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);

    fixture
        .given()
        .events(applicationCreatedEvent(applicationId, details))
        .when()
        .command(
            new MakeApplicationDecisionCommand(
                applicationId,
                0L,
                "REFUSED",
                List.of(
                    new MakeDecisionProceeding(proceedingId, "REFUSED", "reason", "justification")),
                null,
                "{\"overallDecision\":\"REFUSED\"}",
                "Decision recorded",
                occurredAt))
        .then()
        .exception(ApplicationAutoGrantOutcomeConflictException.class)
        .noEvents();
  }

  @Test
  void givenAutoGrantedApplication_whenDecisionMade_thenRejectsWithoutReadingData() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId, details),
            new ApplicationDecisionMadeEvent(
                applicationId,
                1L,
                1L,
                "GRANTED",
                AutoGrantedState.AUTOGRANTED,
                Instant.parse("2026-07-19T10:00:00Z")))
        .when()
        .command(decisionCommand(applicationId, 1L, proceedingId, "REFUSED", "justification", null))
        .then()
        .exception(ApplicationAutoGrantOutcomeConflictException.class)
        .noEvents();
  }

  @Test
  void givenApplicationDataAppendFailure_whenDecisionMade_thenEmitsNoEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    when(applicationDataStore.get(applicationId, 1L))
        .thenReturn(ApplicationDataPayload.from(details).withManualAssessmentRequired());
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any()))
        .thenThrow(new IllegalStateException("application data unavailable"));

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId, details),
            new ApplicationReadyForManualAssessmentEvent(
                applicationId, 1L, 1L, Instant.parse("2026-07-19T10:00:00Z")))
        .when()
        .command(decisionCommand(applicationId, 1L, proceedingId, "REFUSED", "justification", null))
        .then()
        .exception(IllegalStateException.class, "application data unavailable")
        .noEvents();
  }

  @Test
  void givenPreviousDecision_whenDecisionMadeAgain_thenAutomaticallyIncrementsVersion() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    Instant firstOccurredAt = Instant.parse("2026-07-19T10:00:00Z");
    Instant secondOccurredAt = Instant.parse("2026-07-19T10:15:00Z");
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    ApplicationDataPayload current = ApplicationDataPayload.from(details);
    when(applicationDataStore.get(applicationId, 1L)).thenReturn(current);
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any())).thenReturn("hash");

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId, details),
            new ApplicationDecisionMadeEvent(
                applicationId, 1L, 1L, "REFUSED", AutoGrantedState.MANUAL, firstOccurredAt))
        .when()
        .command(
            new MakeApplicationDecisionCommand(
                applicationId,
                1L,
                "REFUSED",
                false,
                List.of(
                    new MakeDecisionProceeding(proceedingId, "REFUSED", "reason", "justification")),
                null,
                "{}",
                null,
                secondOccurredAt))
        .then()
        .events(
            new ApplicationDecisionMadeEvent(
                applicationId, 2L, 2L, "REFUSED", AutoGrantedState.MANUAL, secondOccurredAt));
  }

  @Test
  void givenStaleApplicationVersion_whenDecisionMade_thenRejectsWithoutReadingData() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);

    fixture
        .given()
        .events(applicationCreatedEvent(applicationId, details))
        .when()
        .command(decisionCommand(applicationId, 1L, proceedingId, "REFUSED", "justification", null))
        .then()
        .exception(ApplicationVersionConflictException.class)
        .noEvents();
  }

  @Test
  void givenSubmittedUnassessedApplication_whenMarkedReady_thenStoresFalseOutcomeAndEmitsEvent() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-21T09:30:00Z");
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    when(applicationDataStore.get(applicationId, 0L))
        .thenReturn(ApplicationDataPayload.from(details));
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any())).thenReturn("hash");

    fixture
        .given()
        .events(applicationCreatedEvent(applicationId, details))
        .when()
        .command(new MarkApplicationReadyCommand(applicationId, 0L, "{}", occurredAt))
        .then()
        .resultMessagePayload(ReadyApplicationResult.RECORDED)
        .events(new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, occurredAt));
  }

  @Test
  void givenApplicationDataCannotBeAppended_whenMarkedReady_thenDoesNotEmitEvent() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-21T09:30:00Z");
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    when(applicationDataStore.get(applicationId, 0L))
        .thenReturn(ApplicationDataPayload.from(details));
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any()))
        .thenThrow(new IllegalStateException("application data unavailable"));

    fixture
        .given()
        .events(applicationCreatedEvent(applicationId, details))
        .when()
        .command(new MarkApplicationReadyCommand(applicationId, 0L, "{}", occurredAt))
        .then()
        .exception(IllegalStateException.class)
        .noEvents();
  }

  @Test
  void givenAlreadyManualApplication_whenMarkedReadyAgain_thenSucceedsWithoutAnotherEvent() {
    UUID applicationId = UUID.randomUUID();
    Instant firstOccurredAt = Instant.parse("2026-07-21T09:30:00Z");

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId),
            new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, firstOccurredAt))
        .when()
        .command(
            new MarkApplicationReadyCommand(
                applicationId, 0L, "{}", Instant.parse("2026-07-21T09:31:00Z")))
        .then()
        .resultMessagePayload(ReadyApplicationResult.ALREADY_RECORDED)
        .noEvents();
  }

  @Test
  void givenAutomaticallyGrantedApplication_whenMarkedReady_thenRejectsIncompatibleOutcome() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId),
            new ApplicationDecisionMadeEvent(
                applicationId,
                1L,
                1L,
                "GRANTED",
                AutoGrantedState.AUTOGRANTED,
                Instant.parse("2026-07-21T09:00:00Z")))
        .when()
        .command(
            new MarkApplicationReadyCommand(
                applicationId, 1L, "{}", Instant.parse("2026-07-21T09:31:00Z")))
        .then()
        .exception(ApplicationAutoGrantOutcomeConflictException.class)
        .noEvents();
  }

  @Test
  void givenUnassessedApplicationAndStaleVersion_whenMarkedReady_thenRejectsConflict() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given()
        .events(applicationCreatedEvent(applicationId))
        .when()
        .command(
            new MarkApplicationReadyCommand(
                applicationId, 2L, "{}", Instant.parse("2026-07-21T09:31:00Z")))
        .then()
        .exception(ApplicationVersionConflictException.class)
        .noEvents();
  }

  @Test
  void givenApplicationInProgress_whenMarkedReady_thenRejectsInvalidState() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent inProgress =
        new ApplicationCreatedEvent(
            applicationId,
            0L,
            "fingerprint",
            "APPLICATION_IN_PROGRESS",
            1,
            Instant.parse("2026-07-21T09:00:00Z"),
            null,
            List.of());

    fixture
        .given()
        .events(inProgress)
        .when()
        .command(
            new MarkApplicationReadyCommand(
                applicationId, 0L, "{}", Instant.parse("2026-07-21T09:31:00Z")))
        .then()
        .exception(InvalidApplicationStateException.class)
        .noEvents();
  }

  @Test
  void givenEmptyProceedingsAndMissingGrantedCertificate_whenDecisionMade_thenReportsBothErrors() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    when(applicationDataStore.get(applicationId, 1L))
        .thenReturn(ApplicationDataPayload.from(details).withManualAssessmentRequired());

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId, details),
            new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, Instant.now()))
        .when()
        .command(
            new MakeApplicationDecisionCommand(
                applicationId, 1L, "GRANTED", false, List.of(), null, "{}", null, Instant.now()))
        .then()
        .exception(ValidationException.class)
        .noEvents();
  }

  @Test
  void givenRefusalWithoutJustification_whenDecisionMade_thenRejectsRequest() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    when(applicationDataStore.get(applicationId, 1L))
        .thenReturn(ApplicationDataPayload.from(details).withManualAssessmentRequired());

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId, details),
            new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, Instant.now()))
        .when()
        .command(decisionCommand(applicationId, 1L, proceedingId, "REFUSED", null, null))
        .then()
        .exception(ValidationException.class)
        .noEvents();
  }

  @Test
  void givenDuplicateProceedings_whenDecisionMade_thenRejectsRequest() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    when(applicationDataStore.get(applicationId, 1L))
        .thenReturn(ApplicationDataPayload.from(details).withManualAssessmentRequired());
    MakeDecisionProceeding proceeding =
        new MakeDecisionProceeding(proceedingId, "REFUSED", "reason", "justification");

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId, details),
            new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, Instant.now()))
        .when()
        .command(
            new MakeApplicationDecisionCommand(
                applicationId,
                1L,
                "REFUSED",
                false,
                List.of(proceeding, proceeding),
                null,
                "{}",
                null,
                Instant.now()))
        .then()
        .exception(ValidationException.class)
        .noEvents();
  }

  @Test
  void givenApplication_whenCaseworkerAssigned_thenStoresAuditDataAndEmitsThinEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T11:00:00Z");
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    when(applicationDataStore.get(applicationId, 0L))
        .thenReturn(ApplicationDataPayload.from(details));
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any())).thenReturn("hash");

    fixture
        .given()
        .events(applicationCreatedEvent(applicationId, details))
        .when()
        .command(
            new AssignCaseworkerToApplicationCommand(
                applicationId,
                caseworkerId,
                "{\"caseworkerId\":\"" + caseworkerId + "\"}",
                "Assigned for assessment",
                occurredAt))
        .then()
        .events(
            new ApplicationAssignedToCaseworkerEvent(
                applicationId, 1L, 1L, caseworkerId, occurredAt));
  }

  @Test
  void givenAssignedApplication_whenCaseworkerUnassigned_thenStoresAuditDataAndEmitsThinEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant assignedAt = Instant.parse("2026-07-19T11:00:00Z");
    Instant unassignedAt = Instant.parse("2026-07-20T09:00:00Z");
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    when(applicationDataStore.get(applicationId, 1L))
        .thenReturn(ApplicationDataPayload.from(details).withAssignment("Assigned"));
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any())).thenReturn("hash");

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId, details),
            new ApplicationAssignedToCaseworkerEvent(
                applicationId, 1L, 1L, caseworkerId, assignedAt))
        .when()
        .command(
            new UnassignCaseworkerFromApplicationCommand(
                applicationId, "{}", "Returned to queue", unassignedAt))
        .then()
        .events(new ApplicationUnassignedFromCaseworkerEvent(applicationId, 2L, 2L, unassignedAt));
  }

  @Test
  void givenUnassignedApplication_whenCaseworkerUnassigned_thenDoesNothing() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");

    fixture
        .given()
        .events(applicationCreatedEvent(applicationId))
        .when()
        .command(
            new UnassignCaseworkerFromApplicationCommand(
                applicationId, "{}", "Already unassigned", occurredAt))
        .then()
        .noEvents();
  }

  @Test
  void givenExistingLeadApplication_whenCreateLinkedApplicationGroupCommand_thenEmitsRequested() {
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationCreatedEvent leadCreated = applicationCreatedEvent(leadApplicationId);
    List<UUID> members = List.of(leadApplicationId, UUID.randomUUID());
    java.time.Instant occurredAt = java.time.Instant.parse("2026-07-15T08:00:00Z");
    UUID expectedGroupId =
        UUID.nameUUIDFromBytes(
            ("linked-group:" + leadApplicationId).getBytes(StandardCharsets.UTF_8));

    fixture
        .given()
        .events(leadCreated)
        .when()
        .command(
            new CreateLinkedApplicationGroupCommand(
                leadApplicationId, members.get(1), members, occurredAt))
        .then()
        .events(
            new LinkedApplicationGroupRequested(
                expectedGroupId, leadApplicationId, members, occurredAt));
  }

  @Test
  void givenMissingLeadApplication_whenCreateLinkedApplicationGroupCommand_thenThrowsNotFound() {
    UUID missingLeadId = UUID.randomUUID();
    List<UUID> members = List.of(missingLeadId, UUID.randomUUID());

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(
            new CreateLinkedApplicationGroupCommand(
                missingLeadId, members.get(1), members, Instant.parse("2026-07-15T08:00:00Z")))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
  }

  @Test
  void givenSelfReferentialLead_whenCreateApplication_thenRejectsWithIllegalArgument() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreationDetailsFactory selfLeadFactory =
        new ApplicationCreationDetailsFactory(null, null) {
          @Override
          public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
            return new ApplicationCreationDetails(
                "APPLICATION_SUBMITTED",
                "LAA-123",
                null,
                ApplicationProvider.builder().officeCode("1A001B").build(),
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
                applicationId); // leadApplicationId == self
          }
        };
    fixture.stop();
    fixture = fixtureWith(selfLeadFactory);

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(createCommand(applicationId, "{}"))
        .then()
        .exception(ApplicationGroupInvariantException.class)
        .noEvents();
  }

  @Test
  void
      givenAssociatedApplication_whenCreateLinkedApplicationGroupCommand_thenRejectsWithIllegalState() {
    UUID applicationId = UUID.randomUUID();
    UUID otherLeadId = UUID.randomUUID();
    // Build an event representing this app having been created as an associated member
    ApplicationCreationDetails associatedDetails =
        new ApplicationCreationDetails(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            null,
            ApplicationProvider.builder().officeCode("1A001B").build(),
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
            otherLeadId); // already a member of another group
    ApplicationCreatedEvent associatedCreated =
        applicationCreatedEvent(applicationId, associatedDetails);

    fixture
        .given()
        .events(associatedCreated)
        .when()
        .command(
            new CreateLinkedApplicationGroupCommand(
                applicationId,
                UUID.randomUUID(),
                List.of(applicationId, UUID.randomUUID()),
                Instant.parse("2026-07-15T08:00:00Z")))
        .then()
        .exception(ApplicationGroupInvariantException.class)
        .noEvents();
  }

  @Test
  void givenExistingApplication_whenNoteCreated_thenAdvancesDataVersionAndEmitsEvent() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");
    ApplicationCreatedEvent created =
        applicationCreatedEvent(applicationId, applicationCreationDetails(applicationId));
    ApplicationDataPayload currentPayload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationDataStore.get(applicationId, 0L)).thenReturn(currentPayload);
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any())).thenReturn("hash");

    fixture
        .given()
        .events(created)
        .when()
        .command(new CreateNoteCommand(applicationId, "My note", "{}", occurredAt))
        .then()
        .success()
        .events(new NoteCreatedEvent(applicationId, 1L, occurredAt));
  }

  @Test
  void givenGrantedApplication_whenValidateApplicationGranted_thenSucceedsWithNoEvents() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given()
        .events(
            applicationCreatedEvent(applicationId),
            new ApplicationDecisionMadeEvent(
                applicationId,
                1L,
                1L,
                "GRANTED",
                AutoGrantedState.AUTOGRANTED,
                Instant.parse("2026-07-21T09:00:00Z")))
        .when()
        .command(new ValidateApplicationGrantedCommand(applicationId))
        .then()
        .success()
        .noEvents();
  }

  @Test
  void givenNonGrantedApplication_whenValidateApplicationGranted_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given()
        .events(applicationCreatedEvent(applicationId))
        .when()
        .command(new ValidateApplicationGrantedCommand(applicationId))
        .then()
        .exception(ValidationException.class)
        .noEvents();
  }

  @Test
  void
      givenMissingApplication_whenValidateApplicationGranted_thenThrowsResourceNotFoundException() {
    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(new ValidateApplicationGrantedCommand(UUID.randomUUID()))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
  }

  @Test
  void givenNoApplication_whenCreateNote_thenThrowsResourceNotFoundException() {
    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(new CreateNoteCommand(UUID.randomUUID(), "My note", "{}", Instant.now()))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
  }

  private AxonTestFixture fixtureWith(ApplicationCreationDetailsFactory creationDetailsFactory) {
    return AxonTestFixture.with(
        EventSourcingConfigurer.create()
            .registerEntity(
                EventSourcedEntityModule.autodetected(UUID.class, ApplicationAggregate.class))
            .componentRegistry(
                registry ->
                    registry
                        .registerComponent(
                            ApplicationCreationDetailsFactory.class,
                            configuration -> creationDetailsFactory)
                        .registerComponent(
                            ApplicationDataStore.class, configuration -> applicationDataStore)));
  }

  private CreateApplicationCommand createCommand(UUID applicationId, String serialisedRequest) {
    return createCommandWithSchema(applicationId, serialisedRequest, 1);
  }

  private MakeApplicationDecisionCommand decisionCommand(
      UUID applicationId,
      long expectedVersion,
      UUID proceedingId,
      String decision,
      String justification,
      Map<String, Object> certificate) {
    return new MakeApplicationDecisionCommand(
        applicationId,
        expectedVersion,
        decision,
        false,
        List.of(new MakeDecisionProceeding(proceedingId, decision, "reason", justification)),
        certificate,
        "{}",
        null,
        Instant.now());
  }

  private ApplicationCreationDetails detailsWithProceeding(UUID applicationId, UUID proceedingId) {
    ApplicationCreationDetails original = applicationCreationDetails(applicationId);
    return new ApplicationCreationDetails(
        original.status(),
        original.laaReference(),
        original.client(),
        original.provider(),
        original.opponents(),
        original.allLinkedApplications(),
        original.schemaVersion(),
        original.submittedAt(),
        original.usedDelegatedFunctions(),
        original.categoryOfLaw(),
        original.matterType(),
        List.of(
            Proceeding.builder()
                .id(proceedingId)
                .leadProceeding(true)
                .description("Proceeding")
                .code("SE003")
                .build()),
        original.serialisedRequest(),
        original.occurredAt(),
        original.leadApplicationId());
  }

  private CreateApplicationCommand createCommandWithSchema(
      UUID applicationId, String serialisedRequest, int schemaVersion) {
    return new CreateApplicationCommand(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        validApplicationContent(applicationId, proceedingIdFor(applicationId)),
        serialisedRequest,
        schemaVersion,
        "BaseCivilApplication.json");
  }

  private UUID proceedingIdFor(UUID applicationId) {
    return UUID.nameUUIDFromBytes(("proceeding-" + applicationId).getBytes(StandardCharsets.UTF_8));
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }
}
