package uk.gov.justice.laa.dstew.access.controller.worklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;
import uk.gov.justice.laa.dstew.access.version.VersionToken;

class AssignmentVersionTokensTest {
  @Test
  void givenDirectRow_whenEncoded_thenTokenNamesTheItem() {
    UUID itemId = UUID.randomUUID();
    var item = row(itemId, itemId, 3L);

    assertThat(AssignmentVersionTokens.encode(item))
        .isEqualTo(VersionToken.workItemAssignment(itemId, 3L).encode());
  }

  @Test
  void givenGroupRow_whenEncoded_thenTokenNamesTheGroup() {
    UUID itemId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    var item = row(itemId, groupId, 3L);

    assertThat(AssignmentVersionTokens.encode(item))
        .isEqualTo(VersionToken.workItemAssignment(groupId, 3L).encode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not-a-token"})
  void givenInvalidToken_whenDecoded_thenValidationError(String token) {
    assertThatThrownBy(() -> AssignmentVersionTokens.decode(token))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .contains(AssignmentVersionTokens.INVALID_TOKEN_ERROR));
  }

  private WorkListItemReadModel row(UUID itemId, UUID boundaryId, long version) {
    var item =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, itemId, null, Instant.parse("2026-09-01T10:00:00Z"), 1L, 1L);
    item.setAssignmentBoundaryId(boundaryId);
    item.setAssignmentVersion(version);
    return item;
  }
}
