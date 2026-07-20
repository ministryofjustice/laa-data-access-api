package uk.gov.justice.laa.dstew.access.command.application.data;

/** Contract for encrypting and decrypting application PII payloads. */
public interface EncryptionService {
  byte[] encrypt(String plaintext);

  String decrypt(byte[] ciphertext);
}
