package uk.gov.justice.laa.dstew.access.spike;

/**
 * Simple result object for S3 downloads.
 */
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

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    public String getETag() {
        return eTag;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getS3Url() {
        return s3Url;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public byte[] getContent() {
        return content;
    }

    /**
     * Factory method to create a failed result.
     */
    public static S3DownloadResult failure(String bucket, String key) {
        return new S3DownloadResult(bucket, key, null, false,
                String.format("s3://%s/%s", bucket, key), null, null, null);
    }
}
