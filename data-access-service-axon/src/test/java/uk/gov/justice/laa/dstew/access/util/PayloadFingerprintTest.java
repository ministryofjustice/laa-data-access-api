package uk.gov.justice.laa.dstew.access.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayloadFingerprintTest {

  @Test
  void givenKnownInput_whenComputed_thenReturnsExpectedSha256Digest() {
    assertThat(PayloadFingerprint.compute("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
