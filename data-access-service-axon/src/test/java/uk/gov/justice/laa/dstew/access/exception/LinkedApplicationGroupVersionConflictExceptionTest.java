package uk.gov.justice.laa.dstew.access.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupVersionConflictExceptionTest {

  @Test
  void givenVersionConflict_whenCreated_thenDoesNotClaimVersionExisted() {
    var applicationId = UUID.randomUUID();

    var exception = new LinkedApplicationGroupVersionConflictException(applicationId);

    assertThat(exception)
        .hasMessage(
            "Linked group of application "
                + applicationId
                + " does not match the supplied linkedGroupVersion; re-read before retrying");
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

  @Test
  void givenGroupChanged_whenCreated_thenHasStableMessage() {
    var applicationId = UUID.randomUUID();

    var exception = LinkedApplicationGroupVersionConflictException.groupChanged(applicationId);

    assertThat(exception)
        .hasMessage(
            "Application "
                + applicationId
                + " is no longer in the linked group identified by linkedGroupVersion;"
                + " re-read before retrying");
  }
}
