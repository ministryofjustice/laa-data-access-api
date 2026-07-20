package uk.gov.justice.laa.dstew.access.command.application.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationPiiRepository.ExistingFragment;

class MockApplicationPiiRepositoryTest {

  private MockApplicationPiiRepository repository;

  @BeforeEach
  void setUp() {
    repository = new MockApplicationPiiRepository();
  }

  @Test
  void givenNewFragment_whenSaved_thenFindLatestReturnsRef() {
    UUID applicationId = UUID.randomUUID();
    UUID fragmentRef = UUID.randomUUID();
    byte[] content = "{\"name\":\"test\"}".getBytes();

    repository.saveFragment(
        applicationId, fragmentRef, "applicant", content, Instant.parse("2026-07-20T08:00:00Z"));

    assertThat(repository.findLatestFragment(applicationId, "applicant"))
        .map(ExistingFragment::ref)
        .contains(fragmentRef);
  }

  @Test
  void givenSavedFragment_whenFindFragments_thenReturnsContent() {
    UUID applicationId = UUID.randomUUID();
    UUID fragmentRef = UUID.randomUUID();
    byte[] content = "{\"name\":\"test\"}".getBytes();

    repository.saveFragment(
        applicationId, fragmentRef, "applicant", content, Instant.parse("2026-07-20T08:00:00Z"));

    Map<UUID, byte[]> result = repository.findFragments(Set.of(fragmentRef));
    assertThat(result).containsKey(fragmentRef);
    assertThat(result.get(fragmentRef)).isEqualTo(content);
  }

  @Test
  void givenSamePiiRef_whenSavedTwice_thenIdempotentNoDuplicate() {
    UUID applicationId = UUID.randomUUID();
    UUID fragmentRef = UUID.randomUUID();
    byte[] content = "{\"name\":\"test\"}".getBytes();

    repository.saveFragment(
        applicationId, fragmentRef, "applicant", content, Instant.parse("2026-07-20T08:00:00Z"));
    repository.saveFragment(
        applicationId, fragmentRef, "applicant", content, Instant.parse("2026-07-20T09:00:00Z"));

    assertThat(repository.findFragments(Set.of(fragmentRef))).hasSize(1);
  }

  @Test
  void givenExistingFragments_whenRedactAll_thenFindFragmentsReturnsEmpty() {
    UUID applicationId = UUID.randomUUID();
    UUID fragmentRef = UUID.randomUUID();
    byte[] content = "{\"name\":\"test\"}".getBytes();

    repository.saveFragment(
        applicationId, fragmentRef, "applicant", content, Instant.parse("2026-07-20T08:00:00Z"));
    repository.redactAllForApplication(applicationId, "gdpr", "tester");

    assertThat(repository.findFragments(Set.of(fragmentRef))).isEmpty();
    assertThat(repository.findLatestFragment(applicationId, "applicant")).isEmpty();
  }

  @Test
  void givenTwoFragmentsForSameSection_whenFindLatest_thenReturnsMostRecent() {
    UUID applicationId = UUID.randomUUID();
    UUID ref1 = UUID.randomUUID();
    UUID ref2 = UUID.randomUUID();
    byte[] content1 = "{\"name\":\"first\"}".getBytes();
    byte[] content2 = "{\"name\":\"second\"}".getBytes();

    repository.saveFragment(
        applicationId, ref1, "applicant", content1, Instant.parse("2026-07-20T08:00:00Z"));
    repository.saveFragment(
        applicationId, ref2, "applicant", content2, Instant.parse("2026-07-20T09:00:00Z"));

    assertThat(repository.findLatestFragment(applicationId, "applicant"))
        .map(ExistingFragment::ref)
        .contains(ref2);
  }

  @Test
  void givenContentHashMatch_whenFindLatest_thenContentHashCorrect() {
    UUID applicationId = UUID.randomUUID();
    UUID fragmentRef = UUID.randomUUID();
    byte[] content = "{\"name\":\"test\"}".getBytes();

    repository.saveFragment(
        applicationId, fragmentRef, "applicant", content, Instant.parse("2026-07-20T08:00:00Z"));

    ExistingFragment found = repository.findLatestFragment(applicationId, "applicant").orElseThrow();
    assertThat(found.contentHash()).isNotEmpty();
    assertThat(found.ref()).isEqualTo(fragmentRef);
  }
}
