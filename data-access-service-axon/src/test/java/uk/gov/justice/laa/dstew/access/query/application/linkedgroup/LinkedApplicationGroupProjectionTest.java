package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupProjectionTest {

  @Mock private LinkedApplicationGroupReadRepository groupReadRepository;
  private LinkedApplicationGroupProjection projection;

  @BeforeEach
  void setUp() {
    projection = new LinkedApplicationGroupProjection(groupReadRepository);
  }

  @Test
  void givenGroupCreatedEvent_whenHandled_thenSavesGroupReadModel() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();
    List<UUID> members = List.of(leadId, associatedId);

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            groupId, leadId, members, Instant.parse("2026-07-15T08:00:00Z")));

    verify(groupReadRepository).save(any(LinkedApplicationGroupReadModel.class));
  }

  @Test
  void givenGroupCreatedEvent_whenHandled_thenSavesExactFields() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupReadModel[] saved = new LinkedApplicationGroupReadModel[1];
    doAnswer(
            inv -> {
              saved[0] = inv.getArgument(0);
              return saved[0];
            })
        .when(groupReadRepository)
        .save(any());

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            groupId, leadId, List.of(leadId, memberId), occurredAt));

    assertThat(saved[0].getGroupId()).isEqualTo(groupId);
    assertThat(saved[0].getLeadApplicationId()).isEqualTo(leadId);
    assertThat(saved[0].getMemberIds()).containsExactly(leadId, memberId);
    assertThat(saved[0].getVersion()).isZero();
    assertThat(saved[0].getCreatedAt()).isEqualTo(occurredAt);
    assertThat(saved[0].getModifiedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenMemberAddedEvent_whenHandled_thenAppendsMemberAndSaves() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T09:00:00Z");

    LinkedApplicationGroupReadModel existing =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .leadApplicationId(leadId)
            .memberIds(new ArrayList<>(List.of(leadId, existingMemberId)))
            .createdAt(Instant.parse("2026-07-15T08:00:00Z"))
            .modifiedAt(Instant.parse("2026-07-15T08:00:00Z"))
            .build();

    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(existing));

    projection.on(new MemberAddedToGroupEvent(groupId, leadId, newMemberId, occurredAt));

    assertThat(existing.getMemberIds()).contains(newMemberId);
    assertThat(existing.getVersion()).isEqualTo(1L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(groupReadRepository).save(existing);
  }

  @Test
  void givenDuplicateMemberAddedEvent_whenHandled_thenDoesNotDuplicateOrIncrementVersion() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    LinkedApplicationGroupReadModel existing =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .memberIds(List.of(leadId, memberId))
            .version(1L)
            .build();
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(existing));

    projection.on(new MemberAddedToGroupEvent(groupId, leadId, memberId, Instant.now()));

    assertThat(existing.getMemberIds()).containsExactly(leadId, memberId);
    assertThat(existing.getVersion()).isEqualTo(1L);
  }

  @Test
  void givenMemberAddedToImmutableMembership_whenHandled_thenCopiesAndAddsMember() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    LinkedApplicationGroupReadModel existing =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .memberIds(List.of(leadId, existingMemberId))
            .version(0L)
            .build();
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(existing));

    projection.on(new MemberAddedToGroupEvent(groupId, leadId, newMemberId, Instant.now()));

    assertThat(existing.getMemberIds()).containsExactly(leadId, existingMemberId, newMemberId);
    assertThat(existing.getVersion()).isEqualTo(1L);
  }

  @Test
  void givenLeadChangedEvent_whenHandled_thenUpdatesLeadAndSetsEventVersion() {
    UUID groupId = UUID.randomUUID();
    UUID previousLeadId = UUID.randomUUID();
    UUID newLeadId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T10:00:00Z");
    LinkedApplicationGroupReadModel existing =
        LinkedApplicationGroupReadModel.builder().groupId(groupId).version(1L).build();
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(existing));

    projection.on(
        new LinkedApplicationGroupLeadChangedEvent(
            groupId, previousLeadId, newLeadId, 2L, occurredAt));

    assertThat(existing.getLeadApplicationId()).isEqualTo(newLeadId);
    assertThat(existing.getVersion()).isEqualTo(2L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(groupReadRepository).save(existing);
  }

  @Test
  void givenMemberRemovedEvent_whenHandled_thenRemovesMemberAndSetsEventVersion() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T10:00:00Z");
    LinkedApplicationGroupReadModel existing =
        LinkedApplicationGroupReadModel.builder()
            .groupId(groupId)
            .memberIds(List.of(leadId, memberId))
            .version(1L)
            .build();
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.of(existing));

    projection.on(new MemberRemovedFromGroupEvent(groupId, leadId, memberId, 2L, occurredAt));

    assertThat(existing.getMemberIds()).containsExactly(leadId);
    assertThat(existing.getVersion()).isEqualTo(2L);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(groupReadRepository).save(existing);
  }

  @Test
  void givenDissolvedEvent_whenHandled_thenDeletesGroup() {
    UUID groupId = UUID.randomUUID();
    projection.on(
        new LinkedApplicationGroupDissolvedEvent(
            groupId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            2L,
            Instant.now()));

    verify(groupReadRepository).deleteById(groupId);
  }

  @Test
  void givenMissingGroup_whenLeadChanged_thenDoesNothing() {
    UUID groupId = UUID.randomUUID();
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.empty());

    projection.on(
        new LinkedApplicationGroupLeadChangedEvent(
            groupId, UUID.randomUUID(), UUID.randomUUID(), 2L, Instant.now()));

    verify(groupReadRepository, never()).save(any());
  }

  @Test
  void givenMissingGroup_whenMemberRemoved_thenDoesNothing() {
    UUID groupId = UUID.randomUUID();
    when(groupReadRepository.findById(groupId)).thenReturn(Optional.empty());

    projection.on(
        new MemberRemovedFromGroupEvent(
            groupId, UUID.randomUUID(), UUID.randomUUID(), 2L, Instant.now()));

    verify(groupReadRepository, never()).save(any());
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllGroups() {
    projection.reset();

    verify(groupReadRepository).deleteAllInBatch();
  }
}
