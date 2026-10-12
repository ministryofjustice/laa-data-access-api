package uk.gov.justice.laa.dstew.access.version;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Public, stable resource-type names embedded in version tokens. Never rename an existing name. */
@Getter
@RequiredArgsConstructor
public enum VersionedResourceType {
  LINKED_GROUP("linked-group");

  private final String tokenName;

  /** Finds the resource type for a token name. */
  public static Optional<VersionedResourceType> fromTokenName(String tokenName) {
    return Arrays.stream(values()).filter(type -> type.tokenName.equals(tokenName)).findFirst();
  }
}
