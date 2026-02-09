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
    private final String eTag;
    private final boolean success;
    private final String s3Url;
    private final String contentType;
    private final Long contentLength;
    private final byte[] content;

    public S3DownloadResult(String bucket, String key, String eTag, boolean success,
                           String s3Url, String contentType, Long contentLength, byte[] content) {
        this.bucket = bucket;
        this.key = key;
        this.eTag = eTag;
        this.success = success;
        this.s3Url = s3Url;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.content = content;
    }

    /**
     * Factory method to create a failed result.
     */
    public static S3DownloadResult failure(String bucket, String key) {
        return new S3DownloadResult(bucket, key, null, false,
                String.format("s3://%s/%s", bucket, key), null, null, null);
    }
}
