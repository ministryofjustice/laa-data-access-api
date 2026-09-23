package uk.gov.justice.laa.dstew.access.service.sds;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.dstew.access.exception.FileLengthRequiredException;
import uk.gov.justice.laa.dstew.access.exception.VirusDetectedException;
import uk.gov.justice.laa.dstew.access.exception.VirusScanException;

/** Applies upload-specific error handlers to an SDS {@link RestClient.ResponseSpec}. */
@Component
public class SdsUploadResponseHandler {

  /** Applies upload-specific error handlers and returns the decorated spec. */
  public RestClient.ResponseSpec handle(RestClient.ResponseSpec spec) {
    return spec.onStatus(
            status -> status.isSameCodeAs(HttpStatus.LENGTH_REQUIRED),
            (req, res) -> {
              throw new FileLengthRequiredException("File content length is required");
            })
        .onStatus(
            status -> status.value() == 422,
            (req, res) -> {
              throw new VirusDetectedException("Virus detected in uploaded file");
            })
        .onStatus(
            status ->
                !status.is2xxSuccessful()
                    && !status.isSameCodeAs(HttpStatus.CONFLICT)
                    && !status.isSameCodeAs(HttpStatus.LENGTH_REQUIRED)
                    && status.value() != 422,
            (req, res) -> {
              throw new VirusScanException(
                  "Unexpected response from SDS virus scan: " + res.getStatusCode());
            });
  }
}
