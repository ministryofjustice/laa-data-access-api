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
import uk.gov.justice.laa.dstew.access.api.PriorAuthorityQueryApi;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.DownloadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityUseCase;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityDocumentDownload;

/** HTTP query adapter for retrieving Prior Authority requests. */
@RestController
public class PriorAuthoritiesQueryController implements PriorAuthorityQueryApi {
  private final GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  private final DownloadPriorAuthorityDocumentUseCase downloadPriorAuthorityDocumentUseCase;
  private final GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper;

  /**
   * Constructor for `PriorAuthoritiesQueryController`.
   *
   * @param getPriorAuthorityUseCase Use case for retrieving Prior Authority requests
   * @param getPriorAuthorityResponseMapper Mapper for converting domain models to API responses
   */
  public PriorAuthoritiesQueryController(
      GetPriorAuthorityUseCase getPriorAuthorityUseCase,
      DownloadPriorAuthorityDocumentUseCase downloadPriorAuthorityDocumentUseCase,
      GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper) {
    this.getPriorAuthorityUseCase = getPriorAuthorityUseCase;
    this.downloadPriorAuthorityDocumentUseCase = downloadPriorAuthorityDocumentUseCase;
    this.getPriorAuthorityResponseMapper = getPriorAuthorityResponseMapper;
  }

  /**
   * Retrieves the Prior Authority request identified by the supplied UUID, whether it is still an
   * in-progress draft or has already been submitted.
   *
   * @param serviceName calling service identifier
   * @param priorAuthorityId identifier of the Prior Authority request
   * @return the requested Prior Authority response
   */
  @Override
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  @GetMapping(PriorAuthorityQueryApi.PATH_GET_PRIOR_AUTHORITY)
  public ResponseEntity<PriorAuthorityResponse> getPriorAuthority(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable UUID priorAuthorityId) {
    return ResponseEntity.ok(
        getPriorAuthorityResponseMapper.toResponse(
            getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)));
  }

  /** Streams an owned Prior Authority document using its original filename. */
  @Override
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  @GetMapping(PriorAuthorityQueryApi.PATH_DOWNLOAD_PRIOR_AUTHORITY_DOCUMENT)
  public ResponseEntity<Resource> downloadPriorAuthorityDocument(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable UUID priorAuthorityId,
      @PathVariable UUID documentId) {
    PriorAuthorityDocumentDownload download =
        downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId);

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
