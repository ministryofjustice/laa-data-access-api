package uk.gov.justice.laa.dstew.access.utils.generator.getallnotesforapplication;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class NoteReadModelGenerator
    extends BaseGenerator<NoteReadModel, NoteReadModel.NoteReadModelBuilder> {

  public NoteReadModelGenerator() {
    super(NoteReadModel::toBuilder, NoteReadModel.NoteReadModelBuilder::build);
  }

  @Override
  public NoteReadModel createDefault() {
    return NoteReadModel.builder()
        .applicationId(UUID.randomUUID())
        .notes("A default test note")
        .createdBy("test-user")
        .build();
  }
}
