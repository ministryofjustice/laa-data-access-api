package uk.gov.justice.laa.dstew.access.version;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque identifier for one version of a versioned resource. Encoded as unpadded URL-safe Base64 of
 * {@code v1:<resource-type>:<resource-id>:<version>}. Tokens are not signed; callers must always
 * validate a decoded token against authoritative write-side state.
 */
public record VersionToken(VersionedResourceType resourceType, UUID resourceId, long version) {

  // Versions the token layout (fields, separator, encoding), not any resource's data. Bump it only
  // if the layout changes; decode() would then dispatch on the prefix.
  private static final String FORMAT_VERSION = "v1";
  private static final String SEPARATOR = ":";
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  /** Validates the token components. */
  public VersionToken {
    Objects.requireNonNull(resourceType, "resourceType must not be null");
    Objects.requireNonNull(resourceId, "resourceId must not be null");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  /** Creates a token for a linked application group version. */
  public static VersionToken linkedGroup(UUID groupId, long version) {
    return new VersionToken(VersionedResourceType.LINKED_GROUP, groupId, version);
  }

  /** Encodes this token as an opaque URL-safe string. */
  public String encode() {
    var raw =
        String.join(
            SEPARATOR,
            FORMAT_VERSION,
            resourceType.getTokenName(),
            resourceId.toString(),
            Long.toString(version));
    return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Decodes a token, requiring it to identify a resource of the expected type. */
  public static VersionToken decode(String token, VersionedResourceType expectedType) {
    if (token == null || token.isBlank()) {
      throw invalid();
    }
    String raw;
    try {
      raw = new String(DECODER.decode(token), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw invalid();
    }
    var parts = raw.split(SEPARATOR, -1);
    if (parts.length != 4 || !FORMAT_VERSION.equals(parts[0])) {
      throw invalid();
    }
    var resourceType =
        VersionedResourceType.fromTokenName(parts[1]).orElseThrow(VersionToken::invalid);
    if (resourceType != expectedType) {
      throw invalid();
    }
    return new VersionToken(resourceType, parseUuid(parts[2]), parseVersion(parts[3]));
  }

  private static UUID parseUuid(String value) {
    UUID uuid;
    try {
      uuid = UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw invalid();
    }
    if (!uuid.toString().equals(value)) {
      throw invalid();
    }
    return uuid;
  }

  private static long parseVersion(String value) {
    long version;
    try {
      version = Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw invalid();
    }
    if (version < 0) {
      throw invalid();
    }
    return version;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid version token");
  }
}
