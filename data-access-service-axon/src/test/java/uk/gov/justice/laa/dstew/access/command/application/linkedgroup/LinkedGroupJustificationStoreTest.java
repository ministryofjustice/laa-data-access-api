package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkedGroupJustificationStoreTest {
  @Mock private LinkedGroupJustificationRepository repository;
  @InjectMocks private LinkedGroupJustificationStore store;

  @Test
  void givenOmittedText_whenSaved_thenNoPayload() {
    assertThat(store.save(UUID.randomUUID(), null, Instant.EPOCH)).isNull();
    verifyNoInteractions(repository);
  }

  @Test
  void givenText_whenSaved_thenStoresOnePayloadWithGeneratedReference() {
    var groupId = UUID.randomUUID();
    var id = store.save(groupId, "Shared action reason", Instant.EPOCH);
    var payload = ArgumentCaptor.forClass(LinkedGroupJustification.class);
    verify(repository).saveAndFlush(payload.capture());
    assertThat(payload.getValue().getId()).isEqualTo(id);
    assertThat(payload.getValue().getGroupId()).isEqualTo(groupId);
    assertThat(payload.getValue().getJustification()).isEqualTo("Shared action reason");
    assertThat(payload.getValue().getCreatedAt()).isEqualTo(Instant.EPOCH);
  }

  @Test
  void givenReferences_whenLoaded_thenReturnsBatchWithoutInventingMissingPayloads() {
    var id = UUID.randomUUID();
    var missing = UUID.randomUUID();
    var ids = List.of(id, missing);
    when(repository.findAllById(ids))
        .thenReturn(
            List.of(new LinkedGroupJustification(id, UUID.randomUUID(), "Reason", Instant.EPOCH)));
    assertThat(store.findAll(ids)).containsOnlyKeys(id).containsEntry(id, "Reason");
    verify(repository).findAllById(ids);
  }

  @Test
  void givenNoReferences_whenLoaded_thenNoQuery() {
    assertThat(store.findAll(List.of())).isEmpty();
    verifyNoInteractions(repository);
  }
}
