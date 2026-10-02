package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PriorAuthorityDocumentFormatTest {

  @Test
  void givenValidPdf_whenValidate_thenReturnsPdfFormat() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "evidence.pdf", "application/pdf", "%PDF-1.4 content".getBytes());

    PriorAuthorityDocumentFormat format = PriorAuthorityDocumentFormat.validate(file);

    assertThat(format).isEqualTo(PriorAuthorityDocumentFormat.PDF);
  }

  @Test
  void givenDoubleExtensionFileName_whenValidate_thenThrowsIllegalArgumentException() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "evidence.pdf.exe", "application/pdf", "%PDF-1.4 content".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PriorAuthorityDocumentFormat.validate(file))
        .withMessage("Uploaded file name must not contain multiple file extensions");
  }

  @Test
  void givenTarGzStyleFileName_whenValidate_thenThrowsIllegalArgumentException() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "evidence.tar.gz", "application/pdf", "%PDF-1.4 content".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PriorAuthorityDocumentFormat.validate(file))
        .withMessage("Uploaded file name must not contain multiple file extensions");
  }

  @Test
  void givenBlankFileName_whenValidate_thenThrowsIllegalArgumentException() {
    MockMultipartFile file =
        new MockMultipartFile("file", " ", "application/pdf", "%PDF-1.4 content".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PriorAuthorityDocumentFormat.validate(file))
        .withMessage("Uploaded file name must not be empty");
  }

  @Test
  void givenNullFileName_whenValidate_thenThrowsIllegalArgumentException() {
    MockMultipartFile file =
        new MockMultipartFile("file", null, "application/pdf", "%PDF-1.4 content".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PriorAuthorityDocumentFormat.validate(file))
        .withMessage("Uploaded file name must not be empty");
  }

  @Test
  void givenUnsupportedContentType_whenValidate_thenThrowsIllegalArgumentException() {
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "image/png", "%PDF-1.4 content".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PriorAuthorityDocumentFormat.validate(file))
        .withMessage("Unsupported document content type");
  }

  @Test
  void givenContentNotStartingWithPdfSignature_whenValidate_thenThrowsIllegalArgumentException() {
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "not a pdf".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PriorAuthorityDocumentFormat.validate(file))
        .withMessage("Uploaded file is not a valid PDF document");
  }

  @Test
  void givenContentShorterThanSignature_whenValidate_thenThrowsIllegalArgumentException() {
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%P".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PriorAuthorityDocumentFormat.validate(file))
        .withMessage("Uploaded file is not a valid PDF document");
  }
}
