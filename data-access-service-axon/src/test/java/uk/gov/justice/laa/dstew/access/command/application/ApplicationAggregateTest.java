package uk.gov.justice.laa.dstew.access.command.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class ApplicationAggregateTest {

  private AggregateTestFixture<ApplicationAggregate> fixture;
  private ApplicationCreationDetailsFactory factory;
  private ApplicationDataStore applicationDataStore;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    factory =
        new ApplicationCreationDetailsFactory(null, null) {
          @Override
          public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
            return applicationCreationDetails(command.applicationId());
          }
        };
    fixture.registerInjectableResource(factory);
    applicationDataStore = mock(ApplicationDataStore.class);
    when(applicationDataStore.append(any(), anyLong(), any()))
        .thenAnswer(
            invocation ->
                ApplicationDataStore.fingerprint(
                    invocation.<ApplicationCreationDetails>getArgument(2).serialisedRequest()));
    fixture.registerInjectableResource(applicationDataStore);
  }

  @Test
  void givenNoApplication_whenCreated_thenCreatesApplication() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    ApplicationCreatedEvent expected = applicationCreatedEvent(applicationId, details);

    fixture
        .givenNoPriorActivity()
        .when(createCommand(applicationId, "{}"))
        .expectResultMessagePayload(applicationId)
        .expectEvents(expected);
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
            List.of(),
            1,
            "APPLY",
            applicationId,
            java.time.Instant.parse("2026-07-14T12:30:00Z"),
            "1A001B",
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
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    fixture.registerInjectableResource(factoryWithLead);
    fixture.registerInjectableResource(applicationDataStore);

    ApplicationCreatedEvent createdEvent = applicationCreatedEvent(applicationId, detailsWithLead);

    // ApplicationLinkedEvent is no longer emitted; linking is initiated by
    // ApplicationGroupEventRouter after the projection picks up ApplicationCreatedEvent.
    fixture
        .givenNoPriorActivity()
        .when(createCommand(applicationId, "{}"))
        .expectResultMessagePayload(applicationId)
        .expectEvents(createdEvent);
  }

  @Test
  void givenExistingApplication_whenIdenticalRetry_thenSucceedsIdempotently() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given(existing)
        .when(createCommand(applicationId, "{}"))
        .expectResultMessagePayload(applicationId)
        .expectNoEvents();
  }

  @Test
  void givenExistingApplication_whenDifferentSerialisedRequest_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given(existing)
        .when(createCommand(applicationId, "{\"different\":true}"))
        .expectException(ApplicationCreationConflictException.class)
        .expectNoEvents();
  }

  @Test
  void givenExistingApplication_whenDifferentSchemaVersion_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given(existing)
        .when(createCommandWithSchema(applicationId, "{}", 2))
        .expectException(ApplicationCreationConflictException.class)
        .expectNoEvents();
  }

  @Test
  void givenCurrentApplicationVersion_whenDecisionMade_thenStoresNextVersionAndEmitsThinEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:15:00Z");
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    ApplicationCreatedEvent created = applicationCreatedEvent(applicationId, details);
    ApplicationDataPayload current = ApplicationDataPayload.from(details);
    when(applicationDataStore.get(applicationId, 0L)).thenReturn(current);
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any())).thenReturn("hash");

    fixture
        .given(created)
        .when(
            new MakeApplicationDecisionCommand(
                applicationId,
                0L,
                "REFUSED",
                false,
                List.of(
                    new MakeDecisionProceeding(proceedingId, "REFUSED", "reason", "justification")),
                null,
                "{\"overallDecision\":\"REFUSED\"}",
                "Decision recorded",
                occurredAt))
        .expectEvents(
            new ApplicationDecisionMadeEvent(applicationId, 1L, 1L, "REFUSED", false, occurredAt));
  }

  @Test
  void givenApplicationDataAppendFailure_whenDecisionMade_thenEmitsNoEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    when(applicationDataStore.get(applicationId, 0L))
        .thenReturn(ApplicationDataPayload.from(details));
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any()))
        .thenThrow(new IllegalStateException("application data unavailable"));

    fixture
        .given(applicationCreatedEvent(applicationId, details))
        .when(decisionCommand(applicationId, 0L, proceedingId, "REFUSED", "justification", null))
        .expectException(IllegalStateException.class)
        .expectExceptionMessage("application data unavailable")
        .expectNoEvents();
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
        .given(
            applicationCreatedEvent(applicationId, details),
            new ApplicationDecisionMadeEvent(
                applicationId, 1L, 1L, "REFUSED", false, firstOccurredAt))
        .when(
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
        .expectEvents(
            new ApplicationDecisionMadeEvent(
                applicationId, 2L, 2L, "REFUSED", false, secondOccurredAt));
  }

  @Test
  void givenStaleApplicationVersion_whenDecisionMade_thenRejectsWithoutReadingData() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);

    fixture
        .given(applicationCreatedEvent(applicationId, details))
        .when(decisionCommand(applicationId, 1L, proceedingId, "REFUSED", "justification", null))
        .expectException(ApplicationVersionConflictException.class)
        .expectNoEvents();
  }

  @Test
  void givenEmptyProceedingsAndMissingGrantedCertificate_whenDecisionMade_thenReportsBothErrors() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given(applicationCreatedEvent(applicationId))
        .when(
            new MakeApplicationDecisionCommand(
                applicationId, 0L, "GRANTED", false, List.of(), null, "{}", null, Instant.now()))
        .expectException(ValidationException.class)
        .expectNoEvents();
  }

  @Test
  void givenRefusalWithoutJustification_whenDecisionMade_thenRejectsRequest() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);

    fixture
        .given(applicationCreatedEvent(applicationId, details))
        .when(decisionCommand(applicationId, 0L, proceedingId, "REFUSED", null, null))
        .expectException(ValidationException.class)
        .expectNoEvents();
  }

  @Test
  void givenDuplicateProceedings_whenDecisionMade_thenRejectsRequest() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    MakeDecisionProceeding proceeding =
        new MakeDecisionProceeding(proceedingId, "REFUSED", "reason", "justification");

    fixture
        .given(applicationCreatedEvent(applicationId, details))
        .when(
            new MakeApplicationDecisionCommand(
                applicationId,
                0L,
                "REFUSED",
                false,
                List.of(proceeding, proceeding),
                null,
                "{}",
                null,
                Instant.now()))
        .expectException(ValidationException.class)
        .expectNoEvents();
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
        .given(applicationCreatedEvent(applicationId, details))
        .when(
            new AssignCaseworkerToApplicationCommand(
                applicationId,
                caseworkerId,
                "{\"caseworkerId\":\"" + caseworkerId + "\"}",
                "Assigned for assessment",
                occurredAt))
        .expectEvents(
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
        .given(
            applicationCreatedEvent(applicationId, details),
            new ApplicationAssignedToCaseworkerEvent(
                applicationId, 1L, 1L, caseworkerId, assignedAt))
        .when(
            new UnassignCaseworkerFromApplicationCommand(
                applicationId, "{}", "Returned to queue", unassignedAt))
        .expectEvents(
            new ApplicationUnassignedFromCaseworkerEvent(applicationId, 2L, 2L, unassignedAt));
  }

  @Test
  void givenUnassignedApplication_whenCaseworkerUnassigned_thenDoesNothing() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T09:00:00Z");

    fixture
        .given(applicationCreatedEvent(applicationId))
        .when(
            new UnassignCaseworkerFromApplicationCommand(
                applicationId, "{}", "Already unassigned", occurredAt))
        .expectNoEvents();
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
        .given(leadCreated)
        .when(
            new CreateLinkedApplicationGroupCommand(
                leadApplicationId, members.get(1), members, occurredAt))
        .expectEvents(
            new LinkedApplicationGroupRequested(
                expectedGroupId, leadApplicationId, members, occurredAt));
  }

  @Test
  void givenMissingLeadApplication_whenCreateLinkedApplicationGroupCommand_thenThrowsNotFound() {
    UUID missingLeadId = UUID.randomUUID();
    List<UUID> members = List.of(missingLeadId, UUID.randomUUID());

    fixture
        .givenNoPriorActivity()
        .when(
            new CreateLinkedApplicationGroupCommand(
                missingLeadId,
                members.get(1),
                members,
                java.time.Instant.parse("2026-07-15T08:00:00Z")))
        .expectException(ResourceNotFoundException.class)
        .expectNoEvents();
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
                List.of(),
                1,
                "APPLY",
                applicationId,
                java.time.Instant.parse("2026-07-14T12:30:00Z"),
                "1A001B",
                false,
                null,
                null,
                List.of(),
                "{}",
                java.time.Instant.parse("2026-07-15T08:00:00Z"),
                applicationId); // leadApplicationId == self
          }
        };
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    fixture.registerInjectableResource(selfLeadFactory);
    fixture.registerInjectableResource(applicationDataStore);

    fixture
        .givenNoPriorActivity()
        .when(createCommand(applicationId, "{}"))
        .expectException(ApplicationGroupInvariantException.class)
        .expectNoEvents();
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
            List.of(),
            1,
            "APPLY",
            applicationId,
            java.time.Instant.parse("2026-07-14T12:30:00Z"),
            "1A001B",
            false,
            null,
            null,
            List.of(),
            "{}",
            java.time.Instant.parse("2026-07-15T08:00:00Z"),
            otherLeadId); // already a member of another group
    ApplicationCreatedEvent associatedCreated =
        applicationCreatedEvent(applicationId, associatedDetails);

    fixture
        .given(associatedCreated)
        .when(
            new CreateLinkedApplicationGroupCommand(
                applicationId,
                UUID.randomUUID(),
                List.of(applicationId, UUID.randomUUID()),
                java.time.Instant.parse("2026-07-15T08:00:00Z")))
        .expectException(ApplicationGroupInvariantException.class)
        .expectNoEvents();
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
        .given(created)
        .when(
            new uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand(
                applicationId, "My note", "{}", occurredAt))
        .expectSuccessfulHandlerExecution()
        .expectEvents(
            new uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent(
                applicationId, 1L, occurredAt));
  }

  @Test
  void givenNoApplication_whenCreateNote_thenThrowsAggregateNotFoundException() {
    fixture
        .givenNoPriorActivity()
        .when(
            new uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand(
                UUID.randomUUID(), "My note", "{}", Instant.now()))
        .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class)
        .expectNoEvents();
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
        original.applicationContent(),
        original.individuals(),
        original.schemaVersion(),
        original.applicationType(),
        original.applyApplicationId(),
        original.submittedAt(),
        original.officeCode(),
        original.usedDelegatedFunctions(),
        original.categoryOfLaw(),
        original.matterType(),
        List.of(new ApplicationProceeding(proceedingId, proceedingId, "Proceeding", true, null)),
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
        Map.of("id", applicationId.toString()),
        List.of(),
        serialisedRequest,
        schemaVersion,
        "ApplyApplication.json",
        "APPLY");
  }
}
