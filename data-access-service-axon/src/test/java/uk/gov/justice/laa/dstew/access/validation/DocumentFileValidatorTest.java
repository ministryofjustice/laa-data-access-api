package uk.gov.justice.laa.dstew.access.validation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentFileValidatorTest {

  private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  @Test
  void givenSingleExtensionFileName_whenValidateFileName_thenDoesNotThrow() {
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "content".getBytes());

    assertThatNoException().isThrownBy(() -> DocumentFileValidator.validateFileName(file));
  }

  @Test
  void givenDoubleExtensionFileName_whenValidateFileName_thenThrows() {
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf.exe", "application/pdf", "content".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> DocumentFileValidator.validateFileName(file))
        .withMessage("Uploaded file name must not contain multiple file extensions");
  }

  @Test
  void givenBlankFileName_whenValidateFileName_thenThrows() {
    MockMultipartFile file =
        new MockMultipartFile("file", " ", "application/pdf", "content".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> DocumentFileValidator.validateFileName(file))
        .withMessage("Uploaded file name must not be empty");
  }

  @Test
  void givenNullFileName_whenValidateFileName_thenThrows() {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(null);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> DocumentFileValidator.validateFileName(file))
        .withMessage("Uploaded file name must not be empty");
  }

  @Test
  void givenMatchingSignature_whenValidateSignature_thenDoesNotThrow() {
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-1.4".getBytes());

    assertThatNoException()
        .isThrownBy(() -> DocumentFileValidator.validateSignature(file, PDF_SIGNATURE, "PDF"));
  }

  @Test
  void givenMismatchedSignature_whenValidateSignature_thenThrows() {
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "not a pdf".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> DocumentFileValidator.validateSignature(file, PDF_SIGNATURE, "PDF"))
        .withMessage("Uploaded file is not a valid PDF document");
  }

  @Test
  void givenUnreadableFile_whenValidateSignature_thenThrows() throws java.io.IOException {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getInputStream()).thenThrow(new java.io.IOException("Unable to read file"));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> DocumentFileValidator.validateSignature(file, PDF_SIGNATURE, "PDF"))
        .withMessage("Unable to validate uploaded document")
        .withCauseInstanceOf(java.io.IOException.class);
  }
}
