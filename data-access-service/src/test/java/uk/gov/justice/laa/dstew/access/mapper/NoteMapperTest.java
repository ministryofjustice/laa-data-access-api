package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationNoteResponse;

@ExtendWith(MockitoExtension.class)
class NoteMapperTest extends BaseMapperTest {

  @InjectMocks private NoteMapperImpl mapper;

  @Test
  void givenNullNoteEntity_whenMap_thenReturnNull() {
    assertThat(mapper.toApplicationNoteResponse(null)).isNull();
  }

  @Test
  void givenNullInstant_whenMap_thenReturnsNull() {
    assertThat(mapper.map((Instant) null)).isNull();
  }

  @Test
  void givenInstant_whenMap_thenReturnsOffsetDateTimeAtUtc() {
    Instant instant = Instant.ofEpochSecond(1_000_000);

    assertThat(mapper.map(instant)).isEqualTo(instant.atOffset(ZoneOffset.UTC));
  }

  @Test
  void givenNoteEntity_whenToApplicationNoteResponse_thenMapsFieldsCorrectly() {
    Instant createdAt = Instant.parse("2025-06-01T10:00:00Z");
    NoteEntity entity =
        NoteEntity.builder()
            .id(UUID.randomUUID())
            .applicationId(UUID.randomUUID())
            .notes("this is a note")
            .createdAt(createdAt)
            .createdBy("user-a")
            .build();

    ApplicationNoteResponse result = mapper.toApplicationNoteResponse(entity);

    assertThat(result).isNotNull();
    assertThat(result.getNote()).isEqualTo("this is a note");
    assertThat(result.getCreatedAt())
        .isEqualTo(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    assertThat(result.getCreatedBy()).isEqualTo("user-a");
  }

  @Test
  void givenNoteEntityWithNullCreatedAt_whenToApplicationNoteResponse_thenCreatedAtIsNull() {
    NoteEntity entity =
        NoteEntity.builder().notes("some note").createdAt(null).createdBy("user-b").build();

    ApplicationNoteResponse result = mapper.toApplicationNoteResponse(entity);

    assertThat(result).isNotNull();
    assertThat(result.getCreatedAt()).isNull();
  }

  @Test
  void givenNoteEntityWithAllNullFields_whenToApplicationNoteResponse_thenAllFieldsAreNull() {
    NoteEntity entity = NoteEntity.builder().notes(null).createdAt(null).createdBy(null).build();

    ApplicationNoteResponse result = mapper.toApplicationNoteResponse(entity);

    assertThat(result).isNotNull();
    assertThat(result.getNote()).isNull();
    assertThat(result.getCreatedAt()).isNull();
    assertThat(result.getCreatedBy()).isNull();
  }
}
