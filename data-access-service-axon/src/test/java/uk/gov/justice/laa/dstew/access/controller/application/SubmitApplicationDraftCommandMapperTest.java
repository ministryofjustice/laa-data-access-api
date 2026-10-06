package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;

class SubmitApplicationDraftCommandMapperTest {

  private final SubmitApplicationDraftCommandMapper mapper =
      new SubmitApplicationDraftCommandMapper();

  @Test
  void givenApplicationId_whenMapped_thenApplicationIdIsPreserved() {
    UUID applicationId = UUID.randomUUID();

    SubmitApplicationDraftCommand command = mapper.toSubmitCommand(applicationId);

    assertThat(command.applicationId()).isEqualTo(applicationId);
  }

  @Test
  void givenApplicationId_whenMapped_thenOccurredAtIsNotNull() {
    SubmitApplicationDraftCommand command = mapper.toSubmitCommand(UUID.randomUUID());

    assertThat(command.occurredAt()).isNotNull();
  }
}
