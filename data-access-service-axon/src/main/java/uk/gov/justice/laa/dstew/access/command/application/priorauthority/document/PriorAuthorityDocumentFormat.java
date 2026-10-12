package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.validation.DocumentFileValidator;

/** Accepted file formats for prior-authority document uploads. */
public enum PriorAuthorityDocumentFormat {
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

  /** Returns the API file type, for example {@code PDF}. */
  public String fileType() {
    return fileType;
  }

  String contentType() {
    return contentType;
  }

  /** Returns the extension used for the stored SDS object. */
  public String fileExtension() {
    return fileExtension;
  }

  /** Returns the accepted format for a recorded content type, if any. */
  public static Optional<PriorAuthorityDocumentFormat> fromContentType(String contentType) {
    return Arrays.stream(values())
        .filter(candidate -> candidate.contentType.equalsIgnoreCase(contentType))
        .findFirst();
  }

  static PriorAuthorityDocumentFormat validate(MultipartFile file) {
    DocumentFileValidator.validateFileName(file);
    PriorAuthorityDocumentFormat format =
        fromContentType(file.getContentType())
            .orElseThrow(() -> new IllegalArgumentException("Unsupported document content type"));
    DocumentFileValidator.validateSignature(file, format.signature, format.fileType);
    return format;
  }
}
