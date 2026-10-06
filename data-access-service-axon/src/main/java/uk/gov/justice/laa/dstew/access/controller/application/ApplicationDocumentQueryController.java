package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.ApplicationDocumentQueryApi;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.EvidenceDocumentDownload;
import uk.gov.justice.laa.dstew.access.usecase.application.DownloadApplicationDocumentUseCase;

/** HTTP query adapter for downloading Application documents. */
@RestController
public class ApplicationDocumentQueryController implements ApplicationDocumentQueryApi {
  private final DownloadApplicationDocumentUseCase downloadApplicationDocumentUseCase;

  public ApplicationDocumentQueryController(
      DownloadApplicationDocumentUseCase downloadApplicationDocumentUseCase) {
    this.downloadApplicationDocumentUseCase = downloadApplicationDocumentUseCase;
  }

  /** Streams an owned Application document using its original filename. */
  @Override
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  @GetMapping(ApplicationDocumentQueryApi.PATH_DOWNLOAD_APPLICATION_DOCUMENT)
  public ResponseEntity<Resource> downloadApplicationDocument(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable UUID applicationId,
      @PathVariable UUID documentId) {
    EvidenceDocumentDownload download =
        downloadApplicationDocumentUseCase.downloadDocument(applicationId, documentId);

    ResponseEntity.BodyBuilder response =
        ResponseEntity.ok()
            .contentType(mediaType(download.document().mediaType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(download.document().fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString());
    if (download.document().size() != null) {
      response.contentLength(download.document().size());
    }
    response.header("X-Document-Uploaded-At", download.document().uploadedAt().toString());
    if (download.document().documentType() != null) {
      response.header("X-Document-Type", download.document().documentType());
    }
    return response.body(download.resource());
  }

  private MediaType mediaType(String mediaType) {
    try {
      return mediaType == null
          ? MediaType.APPLICATION_OCTET_STREAM
          : MediaType.parseMediaType(mediaType);
    } catch (IllegalArgumentException exception) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }
}
