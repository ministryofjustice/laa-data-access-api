package uk.gov.justice.laa.dstew.access.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;

class EventTypesTest {

  @Test
  void givenCommandPackage_whenScanned_thenFindsEventClassesByStoredName() {
    assertThat(EventTypes.all())
        .containsEntry("access.NoteCreated", NoteCreatedEvent.class)
        .containsEntry("access.ApplicationUpdated", ApplicationUpdatedEvent.class);
  }

  @Test
  void givenTwoEventClassesWithTheSameName_whenScanned_thenThrows() {
    assertThatThrownBy(
            () ->
                EventTypes.in(
                    EventTypes.EVENTS_PACKAGE,
                    type -> Optional.of(new MessageType("access.Same", "1.0"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("access.Same");
  }
}
