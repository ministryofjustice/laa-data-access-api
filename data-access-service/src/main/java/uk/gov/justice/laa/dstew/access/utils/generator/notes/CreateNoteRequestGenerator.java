package uk.gov.justice.laa.dstew.access.utils.generator.notes;

import java.time.LocalDateTime;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class CreateNoteRequestGenerator
    extends BaseGenerator<CreateNoteRequest, CreateNoteRequest.Builder> {

  public CreateNoteRequestGenerator() {
    super(CreateNoteRequest::toBuilder, CreateNoteRequest.Builder::build);
  }

  @Override
  public CreateNoteRequest createDefault() {
    return CreateNoteRequest.builder()
        .notes("this is a note, created on " + LocalDateTime.now())
        .build();
  }
}
