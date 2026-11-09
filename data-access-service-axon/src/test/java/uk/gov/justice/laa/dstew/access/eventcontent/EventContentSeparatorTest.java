package uk.gov.justice.laa.dstew.access.eventcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;

class EventContentSeparatorTest {

  private static final String UPLOADED = "access.PriorAuthorityDocumentUploaded";
  private static final String ASSIGNED = "access.WorkItemAssigned";

  private final EventContentSeparator separator =
      new EventContentSeparator(
          new EventJson(),
          Map.of(
              UPLOADED, PriorAuthorityDocumentUploadedEvent.class,
              ASSIGNED, WorkItemAssigned.class));

  @Test
  void givenTypeWithContentFields_whenContentRowMissing_thenFails() {
    assertThat(separator.hasContentFields(UPLOADED)).isTrue();
    assertThatThrownBy(() -> separator.requireNoContentFields(UPLOADED, "event-1"))
        .isInstanceOf(EventContentSeparator.MissingEventContentException.class)
        .hasMessage("Event event-1 of type " + UPLOADED + " has no content row");
  }

  @Test
  void givenTypeWithOnlyKeptFields_whenContentRowMissing_thenAccepted() {
    assertThat(separator.hasContentFields(ASSIGNED)).isFalse();
    separator.requireNoContentFields(ASSIGNED, "event-2");
  }
}
