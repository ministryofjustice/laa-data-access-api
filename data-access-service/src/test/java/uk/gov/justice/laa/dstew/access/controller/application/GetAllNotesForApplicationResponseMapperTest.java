package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.getallnotesforapplication.NoteReadModelGenerator;

class GetAllNotesForApplicationResponseMapperTest {

  private GetAllNotesForApplicationResponseMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetAllNotesForApplicationResponseMapper();
  }

  @Test
  void givenListOfNoteReadModels_whenMapped_thenResponseContainsNotes() {
    Instant createdAt = Instant.parse("2024-01-01T10:00:00Z");
    NoteReadModel note =
        DataGenerator.createDefault(
            NoteReadModelGenerator.class,
            b -> b.notes("test note").createdBy("user-a").createdAt(createdAt));

    ApplicationNotesResponse response = mapper.toResponse(List.of(note));

    assertThat(response.getNotes()).hasSize(1);
    assertThat(response.getNotes().get(0).getNote()).isEqualTo("test note");
    assertThat(response.getNotes().get(0).getCreatedBy()).isEqualTo("user-a");
    assertThat(response.getNotes().get(0).getCreatedAt())
        .isEqualTo(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
  }

  @Test
  void givenEmptyList_whenMapped_thenResponseContainsEmptyList() {
    ApplicationNotesResponse response = mapper.toResponse(List.of());

    assertThat(response.getNotes()).isEmpty();
  }

  @Test
  void givenNoteWithNullCreatedAt_whenMapped_thenCreatedAtIsNull() {
    NoteReadModel note =
        DataGenerator.createDefault(NoteReadModelGenerator.class, b -> b.createdAt(null));

    ApplicationNotesResponse response = mapper.toResponse(List.of(note));

    assertThat(response.getNotes().get(0).getCreatedAt()).isNull();
  }
}
