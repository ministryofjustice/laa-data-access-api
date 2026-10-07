package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ExpectedLinkedGroup;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;
import uk.gov.justice.laa.dstew.access.version.VersionToken;
import uk.gov.justice.laa.dstew.access.version.VersionedResourceType;

/** Converts between linked-group state and the public linkedGroupVersion token. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LinkedGroupVersionTokens {

  static final String INVALID_TOKEN_ERROR =
      "linkedGroupVersion: must be a valid linked group version token";

  /** Encodes the group's current version token, or null when the application is not linked. */
  static @Nullable String encode(@Nullable LinkedApplicationGroupReadModel group) {
    return group == null
        ? null
        : VersionToken.linkedGroup(group.getGroupId(), group.getVersion()).encode();
  }

  /** Decodes a non-null request token into the expected group, rejecting malformed tokens. */
  static ExpectedLinkedGroup decode(String token) {
    try {
      var decoded = VersionToken.decode(token, VersionedResourceType.LINKED_GROUP);
      return new ExpectedLinkedGroup(decoded.resourceId(), decoded.version());
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(List.of(INVALID_TOKEN_ERROR));
    }
  }
}
