package uk.gov.justice.laa.dstew.access.command.application.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AesGcmEncryptionServiceTest {

  private AesGcmEncryptionService service;

  @BeforeEach
  void setUp() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    service = new AesGcmEncryptionService(Base64.getEncoder().encodeToString(key));
  }

  @Test
  void givenPlaintext_whenEncryptedAndDecrypted_thenRoundTripSucceeds() {
    byte[] ciphertext = service.encrypt("hello");

    assertThat(service.decrypt(ciphertext)).isEqualTo("hello");
  }

  @Test
  void givenSamePlaintext_whenEncryptedTwice_thenCiphertextsAreDifferent() {
    assertThat(service.encrypt("hello")).isNotEqualTo(service.encrypt("hello"));
  }

  @Test
  void givenCorruptCiphertext_whenDecrypted_thenThrowsDescriptiveException() {
    byte[] ciphertext = service.encrypt("hello");
    ciphertext[ciphertext.length - 1] ^= 1;

    assertThatThrownBy(() -> service.decrypt(ciphertext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to decrypt PII payload");
  }
}
