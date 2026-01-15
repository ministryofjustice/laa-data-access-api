package uk.gov.justice.laa.dstew.access.spike;

/**
 * Simple result object for S3 uploads.
 */
public class S3UploadResult {
    private final String bucket;
    private final String key;
    private final String eTag;
    private final boolean success;
    private final String s3Url;

    public S3UploadResult(String bucket, String key, String eTag, boolean success, String s3Url) {
        this.bucket = bucket;
        this.key = key;
        this.eTag = eTag;
        this.success = success;
        this.s3Url = s3Url;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    public String geteTag() {
        return eTag;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getS3Url() {
        return s3Url;
    }
}

