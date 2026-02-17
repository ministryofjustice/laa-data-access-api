package uk.gov.justice.laa.dstew.access.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Simple result object for S3 downloads.
 */
@Getter
@Setter
public class S3DownloadResult {
  private final String bucket;
  private final String key;
  private final String etag;
  private final boolean success;
  private final String s3Url;
  private final String contentType;
  private final Long contentLength;
  private final byte[] content;

  /**
   * Constructor for S3DownloadResult.
   */
  public S3DownloadResult(String bucketName, String key, String etag, boolean success,
                          String s3Url, String contentType, Long contentLength, byte[] content) {
    this.bucket = bucketName;
    this.key = key;
    this.etag = etag;
    this.success = success;
    this.s3Url = s3Url;
    this.contentType = contentType;
    this.contentLength = contentLength;
    this.content = content;
  }
}
