package uk.gov.justice.laa.dstew.access.command.application.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MakeApplicationDecisionCommandTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-16T10:00:00Z");

  @Test
  @SuppressWarnings("removal")
  void givenLegacyConstructorWithoutAutoGrantedFlag_whenCreated_thenSetsNullCaseworker() {
    UUID applicationId = UUID.randomUUID();
    MakeDecisionProceeding proceeding =
        new MakeDecisionProceeding(UUID.randomUUID(), "REFUSED", "reason", "justification");
    Map<String, Object> certificate = new HashMap<>();
    certificate.put("reference", "CERT-123");

    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId,
            12L,
            "REFUSED",
            List.of(proceeding),
            certificate,
            "{}",
            "decision",
            OCCURRED_AT);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.caseworkerId()).isNull();
    assertThat(command.expectedApplicationVersion()).isEqualTo(12L);
    assertThat(command.certificate()).containsEntry("reference", "CERT-123");
  }

  @Test
  @SuppressWarnings("removal")
  void givenLegacyConstructorWithIgnoredAutoGrantedFlag_whenCreated_thenSetsNullCaseworker() {
    UUID applicationId = UUID.randomUUID();

    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId,
            5L,
            "GRANTED",
            true,
            List.of(new MakeDecisionProceeding(UUID.randomUUID(), "GRANTED", "reason", "just")),
            null,
            "{}",
            null,
            OCCURRED_AT);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.caseworkerId()).isNull();
    assertThat(command.expectedApplicationVersion()).isEqualTo(5L);
    assertThat(command.certificate()).isNull();
  }

  @Test
  void givenCanonicalConstructor_whenCreated_thenCopiesCollectionsDefensively() {
    UUID caseworkerId = UUID.randomUUID();
    List<MakeDecisionProceeding> mutableProceedings = new ArrayList<>();
    mutableProceedings.add(
        new MakeDecisionProceeding(UUID.randomUUID(), "REFUSED", "reason", "justification"));
    Map<String, Object> mutableCertificate = new HashMap<>();
    mutableCertificate.put("reference", "CERT-123");

    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            UUID.randomUUID(),
            caseworkerId,
            1L,
            "REFUSED",
            mutableProceedings,
            mutableCertificate,
            "{}",
            "desc",
            OCCURRED_AT);

    mutableProceedings.add(
        new MakeDecisionProceeding(UUID.randomUUID(), "REFUSED", "reason2", "justification2"));
    mutableCertificate.put("mutated", true);

    assertThat(command.caseworkerId()).isEqualTo(caseworkerId);
    assertThat(command.proceedings()).hasSize(1);
    assertThat(command.certificate()).hasSize(1).containsEntry("reference", "CERT-123");

    assertThatThrownBy(
            () ->
                command
                    .proceedings()
                    .add(
                        new MakeDecisionProceeding(
                            UUID.randomUUID(), "REFUSED", "reason", "justification")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> command.certificate().put("new", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void givenNullCertificate_whenCreated_thenCertificateRemainsNull() {
    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1L,
            "REFUSED",
            List.of(new MakeDecisionProceeding(UUID.randomUUID(), "REFUSED", "reason", "just")),
            null,
            "{}",
            null,
            OCCURRED_AT);

    assertThat(command.certificate()).isNull();
  }
}
