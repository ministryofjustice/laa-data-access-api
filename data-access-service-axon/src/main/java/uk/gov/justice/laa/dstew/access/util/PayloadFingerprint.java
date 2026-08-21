package uk.gov.justice.laa.dstew.access.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Utility for computing a stable SHA-256 fingerprint of a serialised payload. */
public final class PayloadFingerprint {

  private PayloadFingerprint() {}

  /**
   * Computes a lowercase hex SHA-256 digest of the supplied value.
   *
   * @param value the input to hash
   * @return the 64-character lowercase hex SHA-256 digest
   */
  public static String compute(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException cause) {
      throw new IllegalStateException("SHA-256 not available", cause);
    }
  }
}
