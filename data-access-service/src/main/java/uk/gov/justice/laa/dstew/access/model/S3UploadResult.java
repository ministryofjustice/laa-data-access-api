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


}

