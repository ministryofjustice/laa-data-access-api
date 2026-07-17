package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;

class LinkedApplicationGroupProjectionTest {

  private LinkedApplicationGroupReadRepository groupReadRepository;
  private LinkedApplicationGroupProjection projection;

  @BeforeEach
  void setUp() {
    groupReadRepository = mock(LinkedApplicationGroupReadRepository.class);
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

    projection.on(new MemberAddedToGroupEvent(groupId, newMemberId, occurredAt));

    assertThat(existing.getMemberIds()).contains(newMemberId);
    assertThat(existing.getModifiedAt()).isEqualTo(occurredAt);
    verify(groupReadRepository).save(existing);
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllGroups() {
    projection.reset();

    verify(groupReadRepository).deleteAllInBatch();
  }
}
