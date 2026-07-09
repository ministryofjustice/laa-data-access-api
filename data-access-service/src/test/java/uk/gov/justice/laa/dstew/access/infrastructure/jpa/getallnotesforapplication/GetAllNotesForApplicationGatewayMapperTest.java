package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.notes.NoteEntityGenerator;

class GetAllNotesForApplicationGatewayMapperTest {

  private GetAllNotesForApplicationGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetAllNotesForApplicationGatewayMapper();
  }

  @Test
  void givenFullyPopulatedEntity_whenMapped_thenAllFieldsExtracted() {
    UUID applicationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2024-01-01T10:00:00Z");
    NoteEntity entity =
        DataGenerator.createDefault(
            NoteEntityGenerator.class,
            b ->
                b.applicationId(applicationId)
                    .notes("test note")
                    .createdBy("user-a")
                    .createdAt(createdAt));

    NoteReadModel actual = mapper.toNoteReadModel(entity);

    assertThat(actual.applicationId()).isEqualTo(applicationId);
    assertThat(actual.notes()).isEqualTo("test note");
    assertThat(actual.createdBy()).isEqualTo("user-a");
    assertThat(actual.createdAt()).isEqualTo(createdAt);
  }

  @Test
  void givenNullCreatedAt_whenMapped_thenCreatedAtIsNull() {
    NoteEntity entity =
        DataGenerator.createDefault(NoteEntityGenerator.class, b -> b.createdAt(null));

    NoteReadModel actual = mapper.toNoteReadModel(entity);

    assertThat(actual.createdAt()).isNull();
  }
}
