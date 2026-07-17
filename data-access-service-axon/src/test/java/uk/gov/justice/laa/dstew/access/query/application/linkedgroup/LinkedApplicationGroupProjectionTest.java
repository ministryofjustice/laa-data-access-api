package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;

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
  void givenResetCalled_whenHandled_thenDeletesAllGroups() {
    projection.reset();

    verify(groupReadRepository).deleteAllInBatch();
  }
}
