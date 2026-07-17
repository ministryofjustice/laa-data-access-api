package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

class LinkedApplicationGroupProjectionTest {

  private LinkedApplicationGroupReadRepository groupReadRepository;
  private ApplicationReadRepository applicationReadRepository;
  private LinkedApplicationGroupProjection projection;

  @BeforeEach
  void setUp() {
    groupReadRepository = mock(LinkedApplicationGroupReadRepository.class);
    applicationReadRepository = mock(ApplicationReadRepository.class);
    projection =
        new LinkedApplicationGroupProjection(groupReadRepository, applicationReadRepository);
  }

  @Test
  void givenGroupCreatedEvent_whenHandled_thenSavesGroupReadModel() {
    UUID groupId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();
    List<UUID> members = List.of(groupId, associatedId);
    LinkedApplicationGroupCreatedEvent event =
        new LinkedApplicationGroupCreatedEvent(
            groupId, groupId, members, Instant.parse("2026-07-15T08:00:00Z"));

    when(applicationReadRepository.findById(any())).thenReturn(Optional.empty());

    projection.on(event);

    verify(groupReadRepository).save(any(LinkedApplicationGroupReadModel.class));
  }

  @Test
  void givenGroupCreatedEvent_whenHandled_thenSetsIsLeadOnLeadApp() {
    UUID groupId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();
    List<UUID> members = List.of(groupId, associatedId);
    ApplicationReadModel leadModel = ApplicationReadModel.builder().applicationId(groupId).build();
    ApplicationReadModel assocModel =
        ApplicationReadModel.builder().applicationId(associatedId).build();

    when(applicationReadRepository.findById(groupId)).thenReturn(Optional.of(leadModel));
    when(applicationReadRepository.findById(associatedId)).thenReturn(Optional.of(assocModel));

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            groupId, groupId, members, Instant.parse("2026-07-15T08:00:00Z")));

    assertThat(leadModel.getIsLead()).isTrue();
    assertThat(assocModel.getIsLead()).isFalse();
  }

  @Test
  void givenGroupCreatedEvent_whenHandled_thenSetsGroupIdOnAllMembers() {
    UUID groupId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();
    List<UUID> members = List.of(groupId, associatedId);
    ApplicationReadModel leadModel = ApplicationReadModel.builder().applicationId(groupId).build();
    ApplicationReadModel assocModel =
        ApplicationReadModel.builder().applicationId(associatedId).build();

    when(applicationReadRepository.findById(groupId)).thenReturn(Optional.of(leadModel));
    when(applicationReadRepository.findById(associatedId)).thenReturn(Optional.of(assocModel));

    projection.on(
        new LinkedApplicationGroupCreatedEvent(
            groupId, groupId, members, Instant.parse("2026-07-15T08:00:00Z")));

    assertThat(leadModel.getGroupId()).isEqualTo(groupId);
    assertThat(assocModel.getGroupId()).isEqualTo(groupId);
    verify(applicationReadRepository, times(2)).save(any(ApplicationReadModel.class));
  }
}
