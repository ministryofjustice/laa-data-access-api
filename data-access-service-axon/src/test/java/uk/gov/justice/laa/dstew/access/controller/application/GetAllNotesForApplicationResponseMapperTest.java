package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;
import uk.gov.justice.laa.dstew.access.model.ApplicationNoteResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;

class GetAllNotesForApplicationResponseMapperTest {

  private final GetAllNotesForApplicationResponseMapper mapper =
      new GetAllNotesForApplicationResponseMapper();

  @Test
  void givenEmptyList_whenToResponse_thenReturnsEmptyNotes() {
    ApplicationNotesResponse response = mapper.toResponse(List.of());

    assertThat(response.getNotes()).isEmpty();
  }

  @Test
  void givenNotes_whenToResponse_thenMapsNoteTextAndCreatedAt() {
    Instant createdAt = Instant.parse("2026-07-20T10:00:00Z");
    List<ApplicationNote> notes = List.of(new ApplicationNote("First note", createdAt));

    ApplicationNotesResponse response = mapper.toResponse(notes);

    assertThat(response.getNotes()).hasSize(1);
    ApplicationNoteResponse noteResponse = response.getNotes().get(0);
    assertThat(noteResponse.getNote()).isEqualTo("First note");
    assertThat(noteResponse.getCreatedAt())
        .isEqualTo(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    assertThat(noteResponse.getCreatedBy()).isNull();
  }

  @Test
  void givenNoteWithNullCreatedAt_whenToResponse_thenCreatedAtIsNull() {
    List<ApplicationNote> notes = List.of(new ApplicationNote("No timestamp", null));

    ApplicationNotesResponse response = mapper.toResponse(notes);

    assertThat(response.getNotes()).hasSize(1);
    assertThat(response.getNotes().get(0).getCreatedAt()).isNull();
  }

  @Test
  void givenMultipleNotes_whenToResponse_thenPreservesOrder() {
    List<ApplicationNote> notes =
        List.of(
            new ApplicationNote("First", Instant.parse("2026-07-20T10:00:00Z")),
            new ApplicationNote("Second", Instant.parse("2026-07-20T11:00:00Z")));

    ApplicationNotesResponse response = mapper.toResponse(notes);

    assertThat(response.getNotes())
        .extracting(ApplicationNoteResponse::getNote)
        .containsExactly("First", "Second");
  }
}
