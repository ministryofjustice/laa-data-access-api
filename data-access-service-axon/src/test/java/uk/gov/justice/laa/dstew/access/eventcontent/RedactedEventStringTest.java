package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;

class RedactedEventStringTest {

  private static final String CONTENT_SECRET = "sensitive-test-content";
  private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  record Sample(@RetainedField String id, @Nullable String note) {}

  record ThrowingAccessor(String value) {
    @Override
    public String value() {
      throw new IllegalStateException("accessor failure");
    }
  }

  @Test
  void givenContentPresent_whenRendered_thenRetainedShownAndContentHidden() {
    assertThat(RedactedEventString.of(new Sample("id-1", "secret")))
        .isEqualTo("Sample[id=id-1, note=<content>]");
  }

  @Test
  void givenContentAbsent_whenRendered_thenContentShownAsNull() {
    assertThat(RedactedEventString.of(new Sample("id-1", null)))
        .isEqualTo("Sample[id=id-1, note=null]");
  }

  @ParameterizedTest
  @MethodSource("contentEvents")
  void givenContentEvent_whenToString_thenContentIsRedacted(Record event) {
    assertThat(event.toString()).contains("<content>").doesNotContain(CONTENT_SECRET);
  }

  @Test
  void givenAccessorThrows_whenRendered_thenThrowsIllegalStateExceptionWithCause() {
    assertThatThrownBy(() -> RedactedEventString.of(new ThrowingAccessor(CONTENT_SECRET)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot read value")
        .hasCauseInstanceOf(InvocationTargetException.class)
        .cause()
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  private static Stream<Record> contentEvents() {
    return Stream.<Record>of(
        new ApplicationCreatedEvent(
            ID,
            1,
            "fingerprint",
            "DRAFT",
            1,
            Instant.EPOCH,
            null,
            "office",
            "reference",
            List.of(),
            ParsedAppContentDetails.builder().categoryOfLawCode(CONTENT_SECRET).build()),
        new ApplicationDocumentUploadedEvent(
            ID, ID, null, Instant.EPOCH, null, null, "checksum", null, 1L, null, CONTENT_SECRET),
        new ApplicationDecisionMadeEvent(
            ID,
            1,
            1,
            "GRANTED",
            AutoGrantedState.MANUAL,
            Instant.EPOCH,
            "fingerprint",
            Map.of(),
            null,
            null,
            CONTENT_SECRET),
        new ApplicationDraftStartedEvent(
            ID,
            1,
            "fingerprint",
            Instant.EPOCH,
            "office",
            "DRAFT",
            "reference",
            Map.of("applicant", CONTENT_SECRET),
            null),
        new ApplicationUpdatedEvent(
            ID,
            1,
            1,
            "DRAFT",
            "DRAFT",
            Instant.EPOCH,
            List.of(),
            ParsedAppContentDetails.builder().categoryOfLawCode(CONTENT_SECRET).build()),
        new NoteCreatedEvent(ID, 1, Instant.EPOCH, CONTENT_SECRET),
        new PriorAuthorityDecisionMadeEvent(
            ID,
            ID,
            "type",
            1,
            "GRANTED",
            CONTENT_SECRET,
            null,
            null,
            Instant.EPOCH,
            null,
            null,
            null),
        new PriorAuthorityDocumentTypeUpdatedEvent(ID, ID, CONTENT_SECRET, Instant.EPOCH, ID),
        new PriorAuthorityDocumentUploadedEvent(
            ID, ID, Instant.EPOCH, null, null, "checksum", ID, null, null, CONTENT_SECRET),
        new PriorAuthorityDraftStartedEvent(
            ID,
            ID,
            "type",
            1,
            Instant.EPOCH,
            null,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, CONTENT_SECRET, null, null, null)),
        new PriorAuthorityDraftUpdatedEvent(
            ID,
            ID,
            Instant.EPOCH,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, CONTENT_SECRET, null, null, null)),
        new PriorAuthoritySubmittedEvent(
            ID,
            ID,
            "type",
            1,
            1,
            1L,
            Instant.EPOCH,
            "billing",
            new PriorAuthorityContent(
                PriorAuthorityType.EXPERT, CONTENT_SECRET, null, null, null)));
  }
}
