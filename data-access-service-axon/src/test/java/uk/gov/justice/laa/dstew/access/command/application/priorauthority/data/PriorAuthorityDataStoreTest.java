package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityDataStoreTest {

  @Mock private PriorAuthorityDataRepository repository;
  @Mock private ErrorReporter errorReporter;
  private PriorAuthorityDataStore store;

  @BeforeEach
  void setUp() {
    store = new PriorAuthorityDataStore(repository, errorReporter);
  }

  @Test
  void
      givenPayload_whenAppended_thenStoresExactIdApplicationIdPayloadHashAndTimestampAndReturns64CharFingerprint() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent content =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null);
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, content, "request-json", occurredAt);

    String hash =
        store.append(priorAuthorityId, 0L, applicationId, payload, "request-json", occurredAt);

    ArgumentCaptor<PriorAuthorityData> captor = ArgumentCaptor.forClass(PriorAuthorityData.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(hash).isEqualTo(PayloadFingerprint.compute("request-json")).hasSize(64);
    assertThat(captor.getValue().getId()).isEqualTo(new PriorAuthorityDataId(priorAuthorityId, 0L));
    assertThat(captor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getPayload()).isEqualTo(payload);
    assertThat(captor.getValue().getPayloadHash()).isEqualTo(hash);
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenDuplicateVersion_whenRepositoryRejectsInsert_thenFailurePropagates() {
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate prior authority data version"));

    assertThatThrownBy(
            () ->
                store.append(
                    UUID.randomUUID(),
                    1L,
                    UUID.randomUUID(),
                    new PriorAuthorityDataPayload(
                        UUID.randomUUID(), UUID.randomUUID(), null, "req", Instant.now()),
                    "req",
                    Instant.now()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("duplicate prior authority data version");
  }

  @Test
  void givenKnownInput_whenComputeFingerprint_thenReturnsDeterministicSha256() {
    assertThat(PayloadFingerprint.compute("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void givenStoredVersion_whenGet_thenReturnsPriorAuthorityDataPayload() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityDataPayload expectedPayload =
        new PriorAuthorityDataPayload(priorAuthorityId, applicationId, null, "req", Instant.now());
    PriorAuthorityDataId id = new PriorAuthorityDataId(priorAuthorityId, 2L);
    when(repository.findById(id))
        .thenReturn(
            Optional.of(
                PriorAuthorityData.builder()
                    .id(id)
                    .applicationId(applicationId)
                    .payload(expectedPayload)
                    .build()));

    PriorAuthorityDataPayload result = store.get(priorAuthorityId, 2L);

    assertThat(result).isEqualTo(expectedPayload);
  }

  @Test
  void givenMissingVersion_whenGet_thenThrowsExactIllegalStateException() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(repository.findById(new PriorAuthorityDataId(priorAuthorityId, 7L)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.get(priorAuthorityId, 7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Prior authority data not found for submission " + priorAuthorityId + " version 7");
  }

  @Test
  void givenStoredInitialVersion_whenExists_thenReturnsTrue() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(repository.existsById(new PriorAuthorityDataId(priorAuthorityId, 0L))).thenReturn(true);

    assertThat(store.exists(priorAuthorityId)).isTrue();
  }

  @Test
  void givenStoredVersion_whenFindPayload_thenReturnsThatVersion() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(priorAuthorityId, applicationId, null, "req", Instant.now());
    PriorAuthorityDataId id = new PriorAuthorityDataId(priorAuthorityId, 2L);
    when(repository.findById(id))
        .thenReturn(Optional.of(PriorAuthorityData.builder().id(id).payload(payload).build()));

    assertThat(store.findPayload(priorAuthorityId, 2L)).contains(payload);
  }

  @Test
  void givenMissingVersion_whenFindPayload_thenReturnsEmpty() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(repository.findById(new PriorAuthorityDataId(priorAuthorityId, 7L)))
        .thenReturn(Optional.empty());

    assertThat(store.findPayload(priorAuthorityId, 7L)).isEmpty();
  }

  @Test
  void givenRequestedVersions_whenRetrievedSuccessfully_thenDoesNotReportToSentry() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDataId id = new PriorAuthorityDataId(priorAuthorityId, 2L);
    when(repository.findById(id)).thenReturn(Optional.empty());

    store.findPayload(priorAuthorityId, 2L);

    verifyNoInteractions(errorReporter);
  }

  @Test
  void
      givenWrappedJacksonFailure_whenPayloadRetrieved_thenReportsIdentifierAndRethrowsOriginalException() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDataId id = new PriorAuthorityDataId(priorAuthorityId, 4L);
    UnrecognizedPropertyException jacksonException =
        UnrecognizedPropertyException.from(
            mock(JsonParser.class), PriorAuthorityDataPayload.class, "expertType", List.of());
    jacksonException.prependPath(PriorAuthorityDataPayload.class, "content");
    RuntimeException failure = new RuntimeException("Could not deserialize JSON", jacksonException);
    when(repository.findById(id)).thenThrow(failure);

    assertThatThrownBy(() -> store.findPayload(priorAuthorityId, 4L)).isSameAs(failure);

    verify(errorReporter)
        .report(
            argThat(
                message ->
                    message.contains("prior_authority_data JSON deserialization failed")
                        && message.contains(id.toString())
                        && message.contains("expertType")
                        && message.contains("content.expertType")),
            same(failure));
  }

  @Test
  void givenUnwrappedRepositoryFailure_whenPayloadRetrieved_thenReportsIdentifierAndRethrows() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDataId id = new PriorAuthorityDataId(priorAuthorityId, 4L);
    RuntimeException failure = new RuntimeException("Connection reset");
    when(repository.findById(id)).thenThrow(failure);

    assertThatThrownBy(() -> store.findPayload(priorAuthorityId, 4L)).isSameAs(failure);

    verify(errorReporter)
        .report(
            argThat(
                message ->
                    message.contains("prior_authority_data load failed")
                        && message.contains(id.toString())),
            same(failure));
  }

  @Test
  void givenMissingVersion_whenGet_thenReportsLoadFailureToSentry() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDataId id = new PriorAuthorityDataId(priorAuthorityId, 7L);
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.get(priorAuthorityId, 7L))
        .isInstanceOf(IllegalStateException.class);

    verify(errorReporter)
        .report(argThat(message -> message.contains("prior_authority_data load failed")), any());
  }
}
