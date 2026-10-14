package uk.gov.justice.laa.dstew.access.query.application.listindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;

@ExtendWith(MockitoExtension.class)
class ApplicationListIndexProjectionTest {

  @Mock private ApplicationListIndexReadRepository listIndexRepository;
  @Mock private ApplicationDataStore applicationDataStore;
  private ApplicationListIndexProjection projection;

  @BeforeEach
  void setUp() {
    projection = new ApplicationListIndexProjection(listIndexRepository, applicationDataStore);
  }

  private static EventMessage anyMessage() {
    return new GenericEventMessage(
        "test-id", new MessageType(String.class), "test", Map.of(), Instant.now());
  }

  private static ApplicationDataPayload payloadWithoutMatterType(UUID applicationId) {
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    return payload.withApplicationUpdate(
        payload.client(),
        payload.provider(),
        payload.opponents(),
        payload.submittedAt(),
        payload.usedDelegatedFunctions(),
        payload.categoryOfLaw(),
        null,
        payload.categoryOfLawCode(),
        payload.matterTypeCode(),
        List.of(),
        "{}",
        false);
  }

  @Test
  void givenDocumentUpload_whenHandled_thenUpdatesSortTimestampWithoutContentLookup() {
    UUID applicationId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-28T15:10:27.430Z");
    ApplicationListIndexReadModel row =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .streamVersion(7L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(row));

    projection.on(
        new ApplicationDocumentUploadedEvent(
            applicationId,
            UUID.randomUUID(),
            "GATEWAY_EVIDENCE",
            uploadedAt,
            12L,
            "application/pdf",
            "checksum",
            "CIVIL_APPLY",
            1L),
        anyMessage());

    verify(listIndexRepository).save(row);
    assertThat(row.getModifiedAt()).isEqualTo(uploadedAt);
    assertThat(row.getStreamVersion()).isEqualTo(7L);
    verify(applicationDataStore, never()).get(any(), any(Long.class));
  }

  // -------------------------------------------------------------------------
  // ApplicationCreatedEvent
  // -------------------------------------------------------------------------

  @Test
  void givenCreatedEvent_whenHandled_thenInsertsIndexRowWithStatusAndLaaReference() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ApplicationDataPayload payload = payloadWithoutMatterType(applicationId);
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
    assertThat(saved.getMatterType()).isNull();
    assertThat(saved.getStreamVersion()).isZero();
  }

  @Test
  void givenContentSubmittedAtDiffersFromEvent_whenProjected_thenUsesEventTimestamp() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ApplicationDataPayload payload = payloadWithoutMatterType(applicationId);
    when(applicationDataStore.get(applicationId, event.applicationDataVersion()))
        .thenReturn(payload);

    projection.on(event, anyMessage());

    ArgumentCaptor<ApplicationListIndexReadModel> captor =
        ArgumentCaptor.forClass(ApplicationListIndexReadModel.class);
    verify(listIndexRepository).save(captor.capture());
    assertThat(captor.getValue().getSubmittedAt()).isEqualTo(event.occurredAt());
    assertThat(payload.submittedAt()).isNotEqualTo(event.occurredAt());
  }

  @Test
  void givenLegacyPayload_whenReplayed_thenRestoresLeadCodes() throws Exception {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    ObjectNode json =
        mapper.valueToTree(ApplicationDataPayload.from(applicationCreationDetails(applicationId)));
    json.set(
        "proceedings",
        mapper.valueToTree(
            List.of(
                Proceeding.builder()
                    .id(UUID.randomUUID())
                    .leadProceeding(true)
                    .code("SE003")
                    .description("Care order")
                    .categoryOfLawCode("MAT")
                    .matterTypeCode("KPBLW")
                    .build())));
    ApplicationDataPayload payload = mapper.treeToValue(json, ApplicationDataPayload.class);
    when(applicationDataStore.get(applicationId, event.applicationDataVersion()))
        .thenReturn(payload);

    projection.on(event, anyMessage());

    ArgumentCaptor<ApplicationListIndexReadModel> captor =
        ArgumentCaptor.forClass(ApplicationListIndexReadModel.class);
    verify(listIndexRepository).save(captor.capture());
    assertThat(captor.getValue().getCategoryOfLawCode()).isEqualTo("MAT");
    assertThat(captor.getValue().getMatterTypeCode()).isEqualTo("KPBLW");
  }

  @Test
  void givenCreatedEventWithClient_whenHandled_thenPopulatesPiiColumns() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);

    ApplicationClient client =
        ApplicationClient.builder()
            .firstName("Jane")
            .lastName("Smith")
            .dateOfBirth(LocalDate.of(1990, 6, 15))
            .appliedPreviously(false)
            .addresses(List.of())
            .build();
    ApplicationDataPayload basePayload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    ApplicationDataPayload payloadWithClient =
        new ApplicationDataPayload(
            basePayload.laaReference(),
            client,
            basePayload.provider(),
            basePayload.opponents(),
            basePayload.submittedAt(),
            basePayload.usedDelegatedFunctions(),
            basePayload.categoryOfLaw(),
            basePayload.matterType(),
            basePayload.categoryOfLawCode(),
            basePayload.matterTypeCode(),
            basePayload.proceedings(),
            basePayload.serialisedRequest(),
            basePayload.overallDecision(),
            basePayload.autoGranted(),
            basePayload.meritsDecisions(),
            basePayload.certificate(),
            basePayload.decisionSerialisedRequest(),
            basePayload.decisionEventDescription(),
            basePayload.assignmentEventDescription(),
            basePayload.notes(),
            basePayload.documentFilenames());

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
  void givenCreatedEventWithNoClient_whenHandled_thenPiiColumnsAreNull() {
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
  // Linked application group events
  // -------------------------------------------------------------------------

  @Test
  void givenGroupCreatedEvent_whenHandled_thenKeepsLeadUnlinkedAndLinksMembers() {
    UUID leadApplicationId = UUID.randomUUID();
    UUID memberApplicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    ApplicationListIndexReadModel lead =
        ApplicationListIndexReadModel.builder().applicationId(leadApplicationId).build();
    ApplicationListIndexReadModel member =
        ApplicationListIndexReadModel.builder().applicationId(memberApplicationId).build();
    when(listIndexRepository.findById(leadApplicationId)).thenReturn(Optional.of(lead));
    when(listIndexRepository.findById(memberApplicationId)).thenReturn(Optional.of(member));

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            UUID.randomUUID(),
            leadApplicationId,
            List.of(leadApplicationId, memberApplicationId),
            occurredAt),
        anyMessage());

    assertThat(lead.getLeadApplicationId()).isNull();
    assertThat(lead.getModifiedAt()).isEqualTo(occurredAt);
    assertThat(member.getLeadApplicationId()).isEqualTo(leadApplicationId);
    assertThat(member.getModifiedAt()).isEqualTo(occurredAt);
    verify(listIndexRepository).save(lead);
    verify(listIndexRepository).save(member);
  }

  @Test
  void givenMemberAddedToGroupEvent_whenHandled_thenLinksAddedMemberToLead() {
    UUID leadApplicationId = UUID.randomUUID();
    UUID memberApplicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T09:00:00Z");
    ApplicationListIndexReadModel member =
        ApplicationListIndexReadModel.builder().applicationId(memberApplicationId).build();
    when(listIndexRepository.findById(memberApplicationId)).thenReturn(Optional.of(member));

    projection.on(
        new MemberAddedToGroupEvent(
            UUID.randomUUID(), leadApplicationId, memberApplicationId, occurredAt),
        anyMessage());

    assertThat(member.getLeadApplicationId()).isEqualTo(leadApplicationId);
    assertThat(member.getModifiedAt()).isEqualTo(occurredAt);
    verify(listIndexRepository).save(member);
  }

  @Test
  void givenMemberAddedToGroupEventForUnknownApplication_whenHandled_thenDoesNothing() {
    UUID memberApplicationId = UUID.randomUUID();
    when(listIndexRepository.findById(memberApplicationId)).thenReturn(Optional.empty());

    projection.on(
        new MemberAddedToGroupEvent(
            UUID.randomUUID(), UUID.randomUUID(), memberApplicationId, Instant.now()),
        anyMessage());

    verify(listIndexRepository, never()).save(any());
  }

  @Test
  void givenLeadChangedEvent_whenHandled_thenUpdatesLeadAndAllAssociateIndexRows() {
    UUID groupId = UUID.randomUUID();
    UUID previousLeadId = UUID.randomUUID();
    UUID newLeadId = UUID.randomUUID();
    UUID otherMemberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T10:00:00Z");
    EventMessage message = anyMessage();
    ApplicationListIndexReadModel previousLead =
        ApplicationListIndexReadModel.builder().applicationId(previousLeadId).build();
    ApplicationListIndexReadModel newLead =
        ApplicationListIndexReadModel.builder()
            .applicationId(newLeadId)
            .leadApplicationId(previousLeadId)
            .build();
    ApplicationListIndexReadModel otherMember =
        ApplicationListIndexReadModel.builder()
            .applicationId(otherMemberId)
            .leadApplicationId(previousLeadId)
            .build();
    when(listIndexRepository.findAllByLeadApplicationId(previousLeadId))
        .thenReturn(List.of(newLead, otherMember));
    when(listIndexRepository.findById(previousLeadId)).thenReturn(Optional.of(previousLead));

    projection.on(
        new LinkedApplicationGroupLeadChangedEvent(
            groupId, previousLeadId, newLeadId, 2L, occurredAt),
        message);

    assertThat(previousLead.getLeadApplicationId()).isEqualTo(newLeadId);
    assertThat(newLead.getLeadApplicationId()).isNull();
    assertThat(otherMember.getLeadApplicationId()).isEqualTo(newLeadId);
    assertThat(List.of(previousLead, newLead, otherMember))
        .allSatisfy(
            row -> {
              assertThat(row.getModifiedAt()).isEqualTo(occurredAt);
              assertThat(row.getProjectionPosition()).isEqualTo(message.identifier().hashCode());
            });
    verify(listIndexRepository).save(previousLead);
    verify(listIndexRepository).save(newLead);
    verify(listIndexRepository).save(otherMember);
  }

  @Test
  void givenMemberRemovedEvent_whenHandled_thenClearsItsLeadIndex() {
    UUID memberId = UUID.randomUUID();
    ApplicationListIndexReadModel member =
        ApplicationListIndexReadModel.builder()
            .applicationId(memberId)
            .leadApplicationId(UUID.randomUUID())
            .build();
    when(listIndexRepository.findById(memberId)).thenReturn(Optional.of(member));
    Instant occurredAt = Instant.parse("2026-07-15T11:00:00Z");

    projection.on(
        new MemberRemovedFromGroupEvent(
            UUID.randomUUID(), UUID.randomUUID(), memberId, 2L, occurredAt),
        anyMessage());

    assertThat(member.getLeadApplicationId()).isNull();
    assertThat(member.getModifiedAt()).isEqualTo(occurredAt);
    verify(listIndexRepository).save(member);
  }

  @Test
  void givenDissolvedEvent_whenHandled_thenClearsEveryMemberLeadIndex() {
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    ApplicationListIndexReadModel lead =
        ApplicationListIndexReadModel.builder().applicationId(leadId).build();
    ApplicationListIndexReadModel removed =
        ApplicationListIndexReadModel.builder()
            .applicationId(removedId)
            .leadApplicationId(leadId)
            .build();
    when(listIndexRepository.findById(leadId)).thenReturn(Optional.of(lead));
    when(listIndexRepository.findById(removedId)).thenReturn(Optional.of(removed));
    Instant occurredAt = Instant.parse("2026-07-15T11:00:00Z");

    projection.on(
        new LinkedApplicationGroupDissolvedEvent(
            UUID.randomUUID(), leadId, removedId, List.of(leadId, removedId), 2L, occurredAt),
        anyMessage());

    assertThat(lead.getLeadApplicationId()).isNull();
    assertThat(removed.getLeadApplicationId()).isNull();
    verify(listIndexRepository).save(lead);
    verify(listIndexRepository).save(removed);
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
        new ApplicationDecisionMadeEvent(
            applicationId, 3L, 4L, "GRANTED", AutoGrantedState.AUTOGRANTED, Instant.now()),
        anyMessage());

    assertThat(existing.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_GRANTED.name());
    assertThat(existing.getAutoGranted()).isEqualTo(AutoGrantedState.AUTOGRANTED);
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
        new ApplicationDecisionMadeEvent(
            applicationId, 3L, 4L, null, AutoGrantedState.MANUAL, Instant.now()),
        anyMessage());

    assertThat(existing.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
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

  // -------------------------------------------------------------------------
  // ApplicationUpdatedEvent
  // -------------------------------------------------------------------------

  @Test
  void givenUpdatedEvent_whenHandled_thenRefreshesFilterFieldsFromDataStore() {
    UUID applicationId = UUID.randomUUID();
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .status("APPLICATION_SUBMITTED")
            .streamVersion(1L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    ApplicationDataPayload payload = payloadWithoutMatterType(applicationId);
    when(applicationDataStore.get(applicationId, 3L)).thenReturn(payload);

    projection.on(
        new ApplicationUpdatedEvent(
            applicationId, 2L, 3L, "APPLICATION_SUBMITTED", "APPLICATION_UPDATED", Instant.now()),
        anyMessage());

    assertThat(existing.getStatus()).isEqualTo("APPLICATION_UPDATED");
    assertThat(existing.getMatterType()).isNull();
    assertThat(existing.getStreamVersion()).isEqualTo(2L);
    verify(listIndexRepository).save(existing);
  }

  @Test
  void givenUpdatedContent_whenProjected_thenKeepsOriginalSubmittedAt() {
    UUID applicationId = UUID.randomUUID();
    Instant originalSubmittedAt = Instant.parse("2026-07-15T08:00:00Z");
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .submittedAt(originalSubmittedAt)
            .streamVersion(1L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(applicationDataStore.get(applicationId, 3L)).thenReturn(payload);

    projection.on(
        new ApplicationUpdatedEvent(
            applicationId, 2L, 3L, "APPLICATION_SUBMITTED", "APPLICATION_UPDATED", Instant.now()),
        anyMessage());

    assertThat(existing.getSubmittedAt()).isEqualTo(originalSubmittedAt);
    assertThat(payload.submittedAt()).isNotEqualTo(originalSubmittedAt);
  }

  @Test
  void givenUpdatedEventForUnknownApplication_whenHandled_thenDoesNothing() {
    UUID applicationId = UUID.randomUUID();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.empty());

    projection.on(
        new ApplicationUpdatedEvent(applicationId, 2L, 3L, "prev", "new", Instant.now()),
        anyMessage());

    verify(listIndexRepository, never()).save(any());
  }

  // -------------------------------------------------------------------------
  // ApplicationDecisionMadeEvent — REFUSED branch
  // -------------------------------------------------------------------------

  @Test
  void givenRefusedDecision_whenHandled_thenStatusIsApplicationRefused() {
    UUID applicationId = UUID.randomUUID();
    ApplicationListIndexReadModel existing =
        ApplicationListIndexReadModel.builder()
            .applicationId(applicationId)
            .status("APPLICATION_SUBMITTED")
            .streamVersion(0L)
            .build();
    when(listIndexRepository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new ApplicationDecisionMadeEvent(
            applicationId, 3L, 4L, "REFUSED", AutoGrantedState.MANUAL, Instant.now()),
        anyMessage());

    assertThat(existing.getStatus()).isEqualTo("APPLICATION_REFUSED");
    verify(listIndexRepository).save(existing);
  }
}
