package uk.gov.justice.laa.dstew.access.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.springframework.web.multipart.MultipartFile;

/** Reusable file-name and content-signature checks shared by document upload use cases. */
public final class DocumentFileValidator {

  private DocumentFileValidator() {}

  /**
   * Rejects missing/blank names and names containing more than one file extension (e.g. {@code
   * name.pdf.exe}).
   */
  public static void validateFileName(MultipartFile file) {
    String fileName = file.getOriginalFilename();
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("Uploaded file name must not be empty");
    }
    // strip any client-supplied path so only the leaf name is checked for extensions
    String leafName = fileName.replace('\\', '/');
    leafName = leafName.substring(leafName.lastIndexOf('/') + 1);
    if (leafName.split("\\.", -1).length > 2) {
      throw new IllegalArgumentException(
          "Uploaded file name must not contain multiple file extensions");
    }
  }

  /** Verifies the file's leading bytes match {@code signature}, e.g. a PDF's {@code %PDF-}. */
  public static void validateSignature(MultipartFile file, byte[] signature, String fileType) {
    try (InputStream inputStream = file.getInputStream()) {
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
