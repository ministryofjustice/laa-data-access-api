package uk.gov.justice.laa.dstew.access.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupVersionConflictExceptionTest {

  @Test
  void givenApplicationAndExpectedVersion_whenCreated_thenHasStableMessage() {
    var applicationId = UUID.randomUUID();

    var exception = new LinkedApplicationGroupVersionConflictException(applicationId, 5L);

    assertThat(exception)
        .hasMessage(
            "Linked group of application " + applicationId + " has changed since version 5");
  }

  @Test
  void givenTargetNoLongerLinked_whenCreated_thenHasStableMessage() {
    var applicationId = UUID.randomUUID();

    var exception =
        LinkedApplicationGroupVersionConflictException.targetNoLongerLinked(applicationId);

    assertThat(exception)
        .hasMessage(
            "Application "
                + applicationId
                + " is no longer in a linked group; re-read before linking");
  }

  @Test
  void givenVersionMissing_whenCreated_thenHasStableMessage() {
    var applicationId = UUID.randomUUID();

    var exception = LinkedApplicationGroupVersionConflictException.versionRequired(applicationId);

    assertThat(exception)
        .hasMessage(
            "Application "
                + applicationId
                + " is in a linked group; linkedGroupVersion is required");
  }
}
