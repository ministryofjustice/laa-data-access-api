package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OriginalFilenameValidatorTest {

  @ParameterizedTest
  @MethodSource("invalidFilenames")
  void givenInvalidFilename_whenValidate_thenRejectsFilename(
      String filename, String expectedMessage) {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> OriginalFilenameValidator.validate(filename))
        .withMessage(expectedMessage);
  }

  @Test
  void givenUnicodeFilenameWithFutureExtension_whenValidate_thenReturnsFilename() {
    String filename = "evidence-\u00e9.docx";

    assertThat(OriginalFilenameValidator.validate(filename)).isEqualTo(filename);
  }

  private static Stream<Arguments> invalidFilenames() {
    return Stream.of(
        Arguments.of(null, "originalFilename must not be blank"),
        Arguments.of(" ", "originalFilename must not be blank"),
        Arguments.of("evidence\\unsafe.pdf", "originalFilename must not contain path separators"),
        Arguments.of("evidence/unsafe.pdf", "originalFilename must not contain path separators"),
        Arguments.of("evidence\r\n.pdf", "originalFilename must not contain control characters"),
        Arguments.of("evidence", "originalFilename must include a file extension"),
        Arguments.of("evidence.", "originalFilename must include a file extension"),
        Arguments.of(
            "a".repeat(OriginalFilenameValidator.MAX_LENGTH) + ".pdf",
            "originalFilename must not exceed 255 characters"));
  }
}
