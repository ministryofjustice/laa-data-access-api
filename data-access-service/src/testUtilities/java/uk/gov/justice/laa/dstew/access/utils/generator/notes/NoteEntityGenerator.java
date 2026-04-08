package uk.gov.justice.laa.dstew.access.utils.generator.notes;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class NoteEntityGenerator extends BaseGenerator<NoteEntity, NoteEntity.NoteEntityBuilder> {

    public NoteEntityGenerator() {
        super(NoteEntity::toBuilder, NoteEntity.NoteEntityBuilder::build);
    }

    @Override
    public NoteEntity createDefault() {
        return NoteEntity.builder()
                .applicationId(UUID.randomUUID())
                .notes("A default test note")
                .createdBy("test-user")
                .build();
    }
}

