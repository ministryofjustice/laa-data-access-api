package uk.gov.justice.laa.dstew.access.query.application.listindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;

class ApplicationListIndexProjectionTest {

  private ApplicationListIndexReadRepository listIndexRepository;
  private ApplicationDataStore applicationDataStore;
  private ApplicationListIndexProjection projection;

  @BeforeEach
  void setUp() {
    listIndexRepository = mock(ApplicationListIndexReadRepository.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    projection = new ApplicationListIndexProjection(listIndexRepository, applicationDataStore);
  }

  private static EventMessage<?> anyMessage() {
    return GenericEventMessage.asEventMessage("test");
  }

  // -------------------------------------------------------------------------
  // ApplicationCreatedEvent
  // -------------------------------------------------------------------------

  @Test
  void givenCreatedEvent_whenHandled_thenInsertsIndexRowWithStatusAndLaaReference() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationDataStore.get(applicationId, event.applicationDataVersion()))
        .thenReturn(payload);

    projection.on(event, anyMessage());

    ArgumentCaptor<ApplicationListIndexReadModel> captor =
        ArgumentCaptor.forClass(ApplicationListIndexReadModel.class);
    verify(listIndexRepository).save(captor.capture());
    ApplicationListIndexReadModel saved = captor.getValue();

    assertThat(saved.getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getStatus()).isEqualTo(event.status());
    assertThat(saved.getLaaReference()).isEqualTo(payload.laaReference());
    assertThat(saved.getCaseworkerId()).isNull();
    assertThat(saved.getStreamVersion()).isZero();
  }

  @Test
  void givenCreatedEventWithClientIndividual_whenHandled_thenPopulatesPiiColumns() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);

    ApplicationIndividual client =
        new ApplicationIndividual(
            UUID.randomUUID(), "Jane", "Smith", LocalDate.of(1990, 6, 15), null, "CLIENT");
    ApplicationDataPayload basePayload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    // Build a payload with the client individual via the public constructor fields
    ApplicationDataPayload payloadWithClient =
        new ApplicationDataPayload(
            basePayload.laaReference(),
            basePayload.applicationContent(),
            List.of(client),
            basePayload.applyApplicationId(),
            basePayload.submittedAt(),
            basePayload.officeCode(),
            basePayload.usedDelegatedFunctions(),
            basePayload.categoryOfLaw(),
            basePayload.matterType(),
            basePayload.proceedings(),
            basePayload.serialisedRequest(),
            basePayload.overallDecision(),
            basePayload.autoGranted(),
            basePayload.meritsDecisions(),
            basePayload.certificate(),
            basePayload.decisionSerialisedRequest(),
            basePayload.decisionEventDescription(),
            basePayload.assignmentEventDescription(),
            basePayload.notes());

    when(applicationDataStore.get(applicationId, event.applicationDataVersion()))
        .thenReturn(payloadWithClient);

    projection.on(event, anyMessage());

    ArgumentCaptor<ApplicationListIndexReadModel> captor =
        ArgumentCaptor.forClass(ApplicationListIndexReadModel.class);
    verify(listIndexRepository).save(captor.capture());
    ApplicationListIndexReadModel saved = captor.getValue();

    assertThat(saved.getClientFirstName()).isEqualTo("Jane");
    assertThat(saved.getClientLastName()).isEqualTo("Smith");
    assertThat(saved.getClientDateOfBirth()).isEqualTo(LocalDate.of(1990, 6, 15));
  }

  @Test
  void givenCreatedEventWithNoIndividuals_whenHandled_thenPiiColumnsAreNull() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationDataStore.get(applicationId, event.applicationDataVersion()))
        .thenReturn(payload);

    projection.on(event, anyMessage());

    ArgumentCaptor<ApplicationListIndexReadModel> captor =
        ArgumentCaptor.forClass(ApplicationListIndexReadModel.class);
    verify(listIndexRepository).save(captor.capture());
    ApplicationListIndexReadModel saved = captor.getValue();

    assertThat(saved.getClientFirstName()).isNull();
    assertThat(saved.getClientLastName()).isNull();
    assertThat(saved.getClientDateOfBirth()).isNull();
  }

  // -------------------------------------------------------------------------
  // ApplicationLinkedEvent
  // -------------------------------------------------------------------------

  @Test
  void givenLinkedEvent_whenHandled_thenUpdatesLeadApplicationId() {
    UUID applicationId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder().applicationId(applicationId).build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(new ApplicationLinkedEvent(applicationId, leadId, Instant.now()), anyMessage());

    assertThat(existing.getLeadApplicationId()).isEqualTo(leadId);
    verify(listIndexRepository).save(existing);
  }

  @Test
  void givenLinkedEventForUnknownApplication_whenHandled_thenDoesNothing() {
    UUID applicationId = UUID.randomUUID();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.empty());

    projection.on(
        new ApplicationLinkedEvent(applicationId, UUID.randomUUID(), Instant.now()), anyMessage());

    verify(listIndexRepository, never()).save(any());
  }

  // -------------------------------------------------------------------------
  // ApplicationDecisionMadeEvent
  // -------------------------------------------------------------------------

  @Test
  void givenDecisionEvent_whenHandled_thenUpdatesStatusAndAutoGrantedAndStreamVersion() {
    UUID applicationId = UUID.randomUUID();
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .status("APPLICATION_SUBMITTED")
            .streamVersion(0L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new ApplicationDecisionMadeEvent(applicationId, 3L, 4L, "GRANTED", true, Instant.now()),
        anyMessage());

    assertThat(existing.getStatus()).isEqualTo("GRANTED");
    assertThat(existing.getIsAutoGranted()).isTrue();
    assertThat(existing.getStreamVersion()).isEqualTo(3L);
    verify(listIndexRepository).save(existing);
  }

  @Test
  void givenDecisionEventWithNullDecision_whenHandled_thenKeepsExistingStatus() {
    UUID applicationId = UUID.randomUUID();
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .status("APPLICATION_SUBMITTED")
            .streamVersion(0L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new ApplicationDecisionMadeEvent(applicationId, 3L, 4L, null, false, Instant.now()),
        anyMessage());

    assertThat(existing.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    verify(listIndexRepository).save(existing);
  }

  // -------------------------------------------------------------------------
  // ApplicationAssignedToCaseworkerEvent
  // -------------------------------------------------------------------------

  @Test
  void givenAssignmentEvent_whenHandled_thenSetsCaseworkerIdAndStreamVersion() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .streamVersion(0L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new ApplicationAssignedToCaseworkerEvent(
            applicationId, 1L, 2L, caseworkerId, Instant.now()),
        anyMessage());

    assertThat(existing.getCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(existing.getStreamVersion()).isEqualTo(1L);
    verify(listIndexRepository).save(existing);
  }

  // -------------------------------------------------------------------------
  // ApplicationUnassignedFromCaseworkerEvent
  // -------------------------------------------------------------------------

  @Test
  void givenUnassignmentEvent_whenHandled_thenClearsCaseworkerIdAndUpdatesStreamVersion() {
    UUID applicationId = UUID.randomUUID();
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .caseworkerId(UUID.randomUUID())
            .streamVersion(1L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new ApplicationUnassignedFromCaseworkerEvent(applicationId, 2L, 3L, Instant.now()),
        anyMessage());

    assertThat(existing.getCaseworkerId()).isNull();
    assertThat(existing.getStreamVersion()).isEqualTo(2L);
    verify(listIndexRepository).save(existing);
  }

  // -------------------------------------------------------------------------
  // NoteCreatedEvent
  // -------------------------------------------------------------------------

  @Test
  void givenNoteCreatedEvent_whenHandled_thenSavesRowWithoutChangingFilterFields() {
    UUID applicationId = UUID.randomUUID();
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .status("APPLICATION_SUBMITTED")
            .clientFirstName("Jane")
            .streamVersion(1L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(new NoteCreatedEvent(applicationId, 2L, Instant.now()), anyMessage());

    // Filter fields must not be modified
    assertThat(existing.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(existing.getClientFirstName()).isEqualTo("Jane");
    assertThat(existing.getStreamVersion()).isEqualTo(1L);
    verify(listIndexRepository).save(existing);
  }

  // -------------------------------------------------------------------------
  // Reset
  // -------------------------------------------------------------------------

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllIndexRows() {
    projection.reset();

    verify(listIndexRepository).deleteAllInBatch();
  }
}
