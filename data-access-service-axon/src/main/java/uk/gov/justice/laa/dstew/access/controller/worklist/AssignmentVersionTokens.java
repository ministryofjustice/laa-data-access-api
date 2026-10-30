package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.worklist.ExpectedAssignment;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;
import uk.gov.justice.laa.dstew.access.version.VersionToken;
import uk.gov.justice.laa.dstew.access.version.VersionedResourceType;

/** Converts between work-list assignment state and the public assignment version token. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AssignmentVersionTokens {

  static final String INVALID_TOKEN_ERROR =
      "expectedAssignmentVersion: must be a valid assignment version token";

  /** Encodes the assignment boundary owner and current assignment version. */
  static String encode(WorkListItemReadModel item) {
    return VersionToken.workItemAssignment(
            item.getAssignmentBoundaryId(), item.getAssignmentVersion())
        .encode();
  }

  /** Decodes a request token into its expected assignment owner and version. */
  static ExpectedAssignment decode(String token) {
    try {
      var decoded = VersionToken.decode(token, VersionedResourceType.WORK_ITEM_ASSIGNMENT);
      return new ExpectedAssignment(decoded.resourceId(), decoded.version());
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(List.of(INVALID_TOKEN_ERROR));
    }
  }
}
