package uk.gov.justice.laa.dstew.access.command.application.data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** AES-256-GCM implementation of {@link EncryptionService} for production use. */
@Component
@Profile("production")
public class AesGcmEncryptionService implements EncryptionService {

  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecureRandom secureRandom = new SecureRandom();
  private final SecretKeySpec keySpec;

  /**
   * Creates a new encryption service from a base64-encoded 32-byte AES key.
   *
   * @param base64EncodedKey base64-encoded AES-256 key
   */
  public AesGcmEncryptionService(
      @Value("${application.pii.encryption.key}") String base64EncodedKey) {
    byte[] keyBytes = Base64.getDecoder().decode(base64EncodedKey);
    if (keyBytes.length != 32) {
      throw new IllegalArgumentException(
          "application.pii.encryption.key must decode to 32 bytes for AES-256-GCM");
    }
    this.keySpec = new SecretKeySpec(keyBytes, "AES");
  }

  @Override
  public byte[] encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to encrypt PII payload", exception);
    }
  }

  @Override
  public String decrypt(byte[] ciphertext) {
    if (ciphertext.length <= IV_LENGTH_BYTES) {
      throw new IllegalStateException("Ciphertext is too short to contain an AES-GCM IV");
    }
    try {
      ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
      byte[] iv = new byte[IV_LENGTH_BYTES];
      buffer.get(iv);
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to decrypt PII payload", exception);
    }
  }
}
