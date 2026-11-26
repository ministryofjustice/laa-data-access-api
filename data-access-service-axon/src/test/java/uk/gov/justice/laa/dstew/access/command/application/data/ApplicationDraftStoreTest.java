package uk.gov.justice.laa.dstew.access.command.application.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationDraftStoreTest {

  @Mock private ApplicationDraftRepository repository;

  @InjectMocks private ApplicationDraftStore store;

  @Test
  void findVersionReturnsStoredVersionWhenDraftExists() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDraft draft =
        ApplicationDraft.builder().applicationId(applicationId).version(3L).build();
    when(repository.findById(applicationId)).thenReturn(Optional.of(draft));

    assertThat(store.findVersion(applicationId)).contains(3L);
  }

  @Test
  void findVersionReturnsEmptyWhenDraftDoesNotExist() {
    UUID applicationId = UUID.randomUUID();
    when(repository.findById(applicationId)).thenReturn(Optional.empty());

    assertThat(store.findVersion(applicationId)).isEmpty();
  }
}
