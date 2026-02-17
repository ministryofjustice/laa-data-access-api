package uk.gov.justice.laa.dstew.access.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Simple result object for S3 uploads.
 */
@Getter
@Setter
public class S3UploadResult {
  private final String bucket;
  private final String key;
  private final String etag;
  private final boolean success;
  private final String s3Url;

  /**
   * Constructor for S3UploadResult.
   */
  public S3UploadResult(String bucketName, String key, String etag, boolean success, String s3Url) {
    this.bucket = bucketName;
    this.key = key;
    this.etag = etag;
    this.success = success;
    this.s3Url = s3Url;
  }


}

