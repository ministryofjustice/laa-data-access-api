package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.web.multipart.MultipartFile;

/** Accepted file formats for prior-authority document uploads. */
enum PriorAuthorityDocumentFormat {
  PDF("PDF", "application/pdf", ".pdf", "%PDF-");

  private final String fileType;
  private final String contentType;
  private final String fileExtension;
  private final byte[] signature;

  PriorAuthorityDocumentFormat(
      String fileType, String contentType, String fileExtension, String signature) {
    this.fileType = fileType;
    this.contentType = contentType;
    this.fileExtension = fileExtension;
    this.signature = signature.getBytes(StandardCharsets.US_ASCII);
  }

  String fileType() {
    return fileType;
  }

  String contentType() {
    return contentType;
  }

  String fileExtension() {
    return fileExtension;
  }

  static PriorAuthorityDocumentFormat fromFileType(String fileType) {
    return Arrays.stream(values())
        .filter(candidate -> candidate.fileType.equalsIgnoreCase(fileType))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported document file type"));
  }

  static PriorAuthorityDocumentFormat validate(MultipartFile file) {
    PriorAuthorityDocumentFormat format =
        Arrays.stream(values())
            .filter(candidate -> candidate.contentType.equalsIgnoreCase(file.getContentType()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported document content type"));
    format.validateSignature(file);
    return format;
  }

  private void validateSignature(MultipartFile file) {
    try (var inputStream = file.getInputStream()) {
      byte[] header = inputStream.readNBytes(signature.length);
      if (!Arrays.equals(header, signature)) {
        throw new IllegalArgumentException(
            "Uploaded file is not a valid %s document".formatted(fileType));
      }
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to validate uploaded document", exception);
    }
  }
}
