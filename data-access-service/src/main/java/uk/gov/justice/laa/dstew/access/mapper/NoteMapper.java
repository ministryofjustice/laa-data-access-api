package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationNoteResponse;

/**
 * Maps between note entity and note API model.
 */
@Mapper(componentModel = "spring")
public interface NoteMapper {

  @Mapping(source = "notes", target = "note")
  ApplicationNoteResponse toApplicationNoteResponse(NoteEntity entity);

  default OffsetDateTime map(Instant value) {
    return value != null ? value.atOffset(ZoneOffset.UTC) : null;
  }
}


