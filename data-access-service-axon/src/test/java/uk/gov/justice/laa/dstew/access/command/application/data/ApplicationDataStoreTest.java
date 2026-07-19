package uk.gov.justice.laa.dstew.access.command.application.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class ApplicationDataStoreTest {

  private ApplicationDataRepository repository;
  private ApplicationDataStore store;

  @BeforeEach
  void setUp() {
    repository = mock(ApplicationDataRepository.class);
    store = new ApplicationDataStore(repository);
  }

  @Test
  void givenCreationDetails_whenAppended_thenStoresVersionedPayloadAndStableHash() {
    UUID applicationId = UUID.randomUUID();
    var details = applicationCreationDetails(applicationId);

    String hash = store.append(applicationId, 0L, details);

    ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(hash)
        .isEqualTo(ApplicationDataStore.fingerprint(details.serialisedRequest()))
        .hasSize(64);
    assertThat(captor.getValue().getId()).isEqualTo(new ApplicationDataId(applicationId, 0L));
    assertThat(captor.getValue().getPayload()).isEqualTo(ApplicationDataPayload.from(details));
    assertThat(captor.getValue().getPayloadHash()).isEqualTo(hash);
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(details.occurredAt());
  }

  @Test
  void givenReconstructedPayload_whenAppended_thenStoresRequestedVersionAndRequestHash() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");

    store.append(applicationId, 3L, payload, "decision-request", occurredAt);

    ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(new ApplicationDataId(applicationId, 3L));
    assertThat(captor.getValue().getPayload()).isSameAs(payload);
    assertThat(captor.getValue().getPayloadHash())
        .isEqualTo(ApplicationDataStore.fingerprint("decision-request"));
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenDuplicateVersion_whenRepositoryRejectsInsert_thenFailurePropagates() {
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate application data version"));

    assertThatThrownBy(
            () ->
                store.append(UUID.randomUUID(), 1L, applicationCreationDetails(UUID.randomUUID())))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("duplicate application data version");
  }

  @Test
  void givenMissingVersion_whenRetrieved_thenReportsApplicationAndVersion() {
    UUID applicationId = UUID.randomUUID();
    when(repository.findById(new ApplicationDataId(applicationId, 7L)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.get(applicationId, 7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Application data not found for " + applicationId + " version 7");
  }

  @Test
  void givenRequestedVersions_whenRetrieved_thenReturnsOnlyStoredPayloadsByIdentifier() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDataId firstId = new ApplicationDataId(applicationId, 0L);
    ApplicationDataId secondId = new ApplicationDataId(applicationId, 1L);
    ApplicationDataPayload first =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    ApplicationDataPayload second = first.withAssignment("Assigned");
    when(repository.findAllById(List.of(firstId, secondId)))
        .thenReturn(
            List.of(
                ApplicationData.builder().id(firstId).payload(first).build(),
                ApplicationData.builder().id(secondId).payload(second).build()));

    var result = store.getAll(List.of(firstId, secondId));

    assertThat(result).containsEntry(firstId, first).containsEntry(secondId, second);
  }

  @Test
  void givenKnownInput_whenFingerprinted_thenReturnsExpectedSha256Digest() {
    assertThat(ApplicationDataStore.fingerprint("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
